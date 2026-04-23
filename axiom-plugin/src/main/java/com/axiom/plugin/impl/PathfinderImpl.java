package com.axiom.plugin.impl;

import com.axiom.api.game.SceneObject;
import com.axiom.api.util.Pathfinder;
import com.axiom.plugin.impl.game.GameObjectsImpl;
import com.axiom.plugin.impl.world.MovementImpl;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Scene-local pathfinder.
 *
 * This is not a full world walker yet. It improves the old placeholder by:
 * - checking actual scene collision flags instead of a raw distance threshold
 * - finding a reachable local target tile when the exact tile is blocked
 * - advancing toward off-scene targets through the best reachable local waypoint
 *
 * This gives the scripts a safer local return-path primitive without trying
 * to solve global routing in one change.
 */
@Slf4j
@Singleton
public class PathfinderImpl implements Pathfinder
{
    private static final int SCENE_SIZE = 104;
    private static final int MAX_ROUTE_ADVANCE = 12;
    private static final int STUCK_ROUTE_ADVANCE = 3;
    private static final int ARRIVAL_DISTANCE = 1;
    private static final int STUCK_THRESHOLD = 5;
    private static final int OBSTACLE_SEARCH_RADIUS = 2;
    private static final int STUCK_OBSTACLE_SEARCH_RADIUS = 4;
    private static final String[] PLANE_UP_ACTIONS = {
        "Climb-up", "Climb Up", "Climb", "Ascend", "Walk-up"
    };
    private static final String[] PLANE_DOWN_ACTIONS = {
        "Climb-down", "Climb Down", "Climb", "Descend", "Walk-down"
    };
    private static final String[] TRAVERSE_ACTIONS = {
        "Open", "Go-through", "Go Through", "Pass", "Cross", "Enter", "Exit",
        "Squeeze-through", "Squeeze Through", "Climb-over", "Climb Over", "Walk-through"
    };

    private static final int[] DX = { 0, 1, 0, -1 };
    private static final int[] DY = { 1, 0, -1, 0 };
    private static final int[] BLOCK_FROM = {
        CollisionDataFlag.BLOCK_MOVEMENT_NORTH,
        CollisionDataFlag.BLOCK_MOVEMENT_EAST,
        CollisionDataFlag.BLOCK_MOVEMENT_SOUTH,
        CollisionDataFlag.BLOCK_MOVEMENT_WEST
    };
    private static final int[] BLOCK_TO = {
        CollisionDataFlag.BLOCK_MOVEMENT_SOUTH,
        CollisionDataFlag.BLOCK_MOVEMENT_WEST,
        CollisionDataFlag.BLOCK_MOVEMENT_NORTH,
        CollisionDataFlag.BLOCK_MOVEMENT_EAST
    };

    private final Client      client;
    private final GameObjectsImpl gameObjects;
    private final MovementImpl movement;
    private TargetKey activeTarget;
    private WorldPoint lastIssuedWaypoint;
    private WorldPoint lastPlayerPosition;
    private int lastDistanceToTarget = Integer.MAX_VALUE;
    private int stagnantTicks = 0;
    private ProgressState lastProgressState = ProgressState.IDLE;

    @Inject
    public PathfinderImpl(Client client, GameObjectsImpl gameObjects, MovementImpl movement)
    {
        this.client   = client;
        this.gameObjects = gameObjects;
        this.movement = movement;
    }

    @Override
    public void walkTo(int worldX, int worldY, int plane)
    {
        Player player = client.getLocalPlayer();
        if (player == null)
        {
            log.warn("PathfinderImpl: no local player available");
            lastProgressState = ProgressState.UNREACHABLE;
            return;
        }

        TargetKey targetKey = new TargetKey(worldX, worldY, plane);
        WorldPoint playerPos = player.getWorldLocation();
        if (!Objects.equals(activeTarget, targetKey))
        {
            resetProgress(targetKey, playerPos, worldX, worldY);
        }

        if (worldDistance(playerPos.getX(), playerPos.getY(), worldX, worldY) <= ARRIVAL_DISTANCE)
        {
            lastProgressState = ProgressState.ARRIVED;
            stagnantTicks = 0;
            lastDistanceToTarget = 0;
            lastPlayerPosition = playerPos;
            return;
        }

        if (client.getPlane() != plane)
        {
            if (tryPlaneTransition(playerPos, plane))
            {
                lastProgressState = ProgressState.WALKING;
            }
            else
            {
                log.warn("PathfinderImpl: target plane {} != current plane {}", plane, client.getPlane());
                lastProgressState = ProgressState.UNREACHABLE;
            }
            return;
        }

        CollisionData collisionData = getCollisionData(plane);
        if (collisionData == null)
        {
            log.debug("PathfinderImpl: no collision map available, falling back to direct walk");
            updateMovementProgress(playerPos, worldX, worldY);
            movement.walkTo(worldX, worldY);
            return;
        }

        LocalPoint playerLocal = LocalPoint.fromWorld(client, playerPos);
        LocalPoint targetLocal = LocalPoint.fromWorld(client, worldX, worldY);

        if (playerLocal == null)
        {
            log.warn("PathfinderImpl: player local position is unavailable");
            lastProgressState = ProgressState.UNREACHABLE;
            return;
        }

        SearchResult search = search(playerLocal.getSceneX(), playerLocal.getSceneY(), collisionData);
        if (search == null)
        {
            log.warn("PathfinderImpl: failed to build local path search from ({},{},{})",
                playerPos.getX(), playerPos.getY(), plane);
            lastProgressState = ProgressState.UNREACHABLE;
            return;
        }

        WorldPoint nextWaypoint = null;
        if (targetLocal != null)
        {
            int targetSceneX = targetLocal.getSceneX();
            int targetSceneY = targetLocal.getSceneY();

            if (search.visited[targetSceneX][targetSceneY])
            {
                nextWaypoint = buildRouteWaypoint(
                    search,
                    targetSceneX,
                    targetSceneY,
                    plane,
                    stagnantTicks >= STUCK_THRESHOLD ? STUCK_ROUTE_ADVANCE : MAX_ROUTE_ADVANCE);
            }
        }

        if (nextWaypoint == null)
        {
            nextWaypoint = findBestReachableWaypoint(
                search,
                playerPos,
                worldX,
                worldY,
                plane,
                stagnantTicks >= STUCK_THRESHOLD ? lastIssuedWaypoint : null
            );
        }

        if (nextWaypoint == null || nextWaypoint.equals(playerPos))
        {
            if (tryTraverseObstacle(playerPos, worldX, worldY, plane, stagnantTicks >= STUCK_THRESHOLD))
            {
                lastProgressState = stagnantTicks >= STUCK_THRESHOLD
                    ? ProgressState.STUCK
                    : ProgressState.WALKING;
                return;
            }
            log.warn("PathfinderImpl: no usable local waypoint toward ({},{},{})",
                worldX, worldY, plane);
            lastProgressState = ProgressState.UNREACHABLE;
            return;
        }

        if (stagnantTicks >= STUCK_THRESHOLD
            && tryTraverseObstacle(playerPos, worldX, worldY, plane, true))
        {
            lastProgressState = ProgressState.STUCK;
            return;
        }

        updateMovementProgress(playerPos, worldX, worldY);
        if (stagnantTicks >= STUCK_THRESHOLD)
        {
            lastProgressState = ProgressState.STUCK;
            log.warn("PathfinderImpl: movement appears stuck toward ({},{},{}) — retrying via alternate waypoint ({},{},{})",
                worldX, worldY, plane, nextWaypoint.getX(), nextWaypoint.getY(), nextWaypoint.getPlane());
        }
        else
        {
            lastProgressState = ProgressState.WALKING;
        }

        lastIssuedWaypoint = nextWaypoint;
        log.debug("PathfinderImpl: walking to local waypoint ({},{},{}) toward target ({},{},{})",
            nextWaypoint.getX(), nextWaypoint.getY(), nextWaypoint.getPlane(), worldX, worldY, plane);
        movement.walkTo(nextWaypoint.getX(), nextWaypoint.getY());
    }

    @Override
    public boolean isReachable(int worldX, int worldY, int plane)
    {
        if (client.getPlane() != plane) return false;

        Player player = client.getLocalPlayer();
        if (player == null) return false;

        CollisionData collisionData = getCollisionData(plane);
        if (collisionData == null) return false;

        LocalPoint playerLocal = LocalPoint.fromWorld(client, player.getWorldLocation());
        LocalPoint targetLocal = LocalPoint.fromWorld(client, worldX, worldY);
        if (playerLocal == null || targetLocal == null) return false;

        SearchResult search = search(playerLocal.getSceneX(), playerLocal.getSceneY(), collisionData);
        return search != null && search.visited[targetLocal.getSceneX()][targetLocal.getSceneY()];
    }

    @Override
    public ProgressState getProgressTo(int worldX, int worldY, int plane)
    {
        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return ProgressState.UNREACHABLE;
        }

        WorldPoint playerPos = player.getWorldLocation();
        if (playerPos.getPlane() == plane
            && worldDistance(playerPos.getX(), playerPos.getY(), worldX, worldY) <= ARRIVAL_DISTANCE)
        {
            return ProgressState.ARRIVED;
        }

        TargetKey targetKey = new TargetKey(worldX, worldY, plane);
        if (Objects.equals(activeTarget, targetKey))
        {
            return lastProgressState;
        }

        return ProgressState.IDLE;
    }

    private CollisionData getCollisionData(int plane)
    {
        CollisionData[] maps = client.getCollisionMaps();
        if (maps == null || plane < 0 || plane >= maps.length) return null;
        return maps[plane];
    }

    private SearchResult search(int startSceneX, int startSceneY, CollisionData collisionData)
    {
        int[][] flags = collisionData.getFlags();
        if (flags == null
            || startSceneX < 0 || startSceneX >= SCENE_SIZE
            || startSceneY < 0 || startSceneY >= SCENE_SIZE)
        {
            return null;
        }

        boolean[][] visited = new boolean[SCENE_SIZE][SCENE_SIZE];
        int[][] distance = new int[SCENE_SIZE][SCENE_SIZE];
        int[][] previousX = new int[SCENE_SIZE][SCENE_SIZE];
        int[][] previousY = new int[SCENE_SIZE][SCENE_SIZE];
        for (int[] row : distance)
        {
            Arrays.fill(row, Integer.MAX_VALUE);
        }
        for (int[] row : previousX)
        {
            Arrays.fill(row, -1);
        }
        for (int[] row : previousY)
        {
            Arrays.fill(row, -1);
        }

        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{ startSceneX, startSceneY });
        visited[startSceneX][startSceneY] = true;
        distance[startSceneX][startSceneY] = 0;

        while (!queue.isEmpty())
        {
            int[] current = queue.removeFirst();
            int x = current[0];
            int y = current[1];

            for (int dir = 0; dir < DX.length; dir++)
            {
                int nx = x + DX[dir];
                int ny = y + DY[dir];

                if (nx < 0 || nx >= SCENE_SIZE || ny < 0 || ny >= SCENE_SIZE)
                {
                    continue;
                }

                if (visited[nx][ny] || !canStep(flags, x, y, nx, ny, dir))
                {
                    continue;
                }

                visited[nx][ny] = true;
                distance[nx][ny] = distance[x][y] + 1;
                previousX[nx][ny] = x;
                previousY[nx][ny] = y;
                queue.addLast(new int[]{ nx, ny });
            }
        }

        return new SearchResult(visited, distance, previousX, previousY);
    }

    private boolean canStep(int[][] flags, int x, int y, int nx, int ny, int dir)
    {
        int fromFlags = flags[x][y];
        int toFlags = flags[nx][ny];

        if (isHardBlocked(toFlags))
        {
            return false;
        }

        return (fromFlags & BLOCK_FROM[dir]) == 0
            && (toFlags & BLOCK_TO[dir]) == 0;
    }

    private boolean isHardBlocked(int flags)
    {
        int hardBlockMask = CollisionDataFlag.BLOCK_MOVEMENT_FULL
            | CollisionDataFlag.BLOCK_MOVEMENT_OBJECT
            | CollisionDataFlag.BLOCK_MOVEMENT_FLOOR
            | CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION;
        return (flags & hardBlockMask) != 0;
    }

    private WorldPoint findBestReachableWaypoint(
        SearchResult search,
        WorldPoint playerPos,
        int targetWorldX,
        int targetWorldY,
        int plane,
        WorldPoint excludedWaypoint)
    {
        int bestSceneX = -1;
        int bestSceneY = -1;
        int bestDistanceToTarget = worldDistance(playerPos.getX(), playerPos.getY(), targetWorldX, targetWorldY);
        int bestPathDistance = -1;

        for (int x = 0; x < SCENE_SIZE; x++)
        {
            for (int y = 0; y < SCENE_SIZE; y++)
            {
                if (!search.visited[x][y])
                {
                    continue;
                }

                LocalPoint localPoint = LocalPoint.fromScene(x, y, client.getTopLevelWorldView());
                if (localPoint == null)
                {
                    continue;
                }

                WorldPoint candidate = WorldPoint.fromLocalInstance(client, localPoint, plane);
                if (candidate == null)
                {
                    continue;
                }
                if (excludedWaypoint != null && excludedWaypoint.equals(candidate))
                {
                    continue;
                }

                int distanceToTarget = worldDistance(candidate.getX(), candidate.getY(), targetWorldX, targetWorldY);
                int pathDistance = search.distance[x][y];

                if (distanceToTarget < bestDistanceToTarget
                    || (distanceToTarget == bestDistanceToTarget && pathDistance > bestPathDistance))
                {
                    bestSceneX = x;
                    bestSceneY = y;
                    bestDistanceToTarget = distanceToTarget;
                    bestPathDistance = pathDistance;
                }
            }
        }

        if (bestSceneX == -1)
        {
            return null;
        }

        LocalPoint localPoint = LocalPoint.fromScene(bestSceneX, bestSceneY, client.getTopLevelWorldView());
        return localPoint != null ? WorldPoint.fromLocalInstance(client, localPoint, plane) : null;
    }

    private WorldPoint buildRouteWaypoint(
        SearchResult search,
        int targetSceneX,
        int targetSceneY,
        int plane,
        int routeAdvance)
    {
        Deque<int[]> route = new ArrayDeque<>();
        int x = targetSceneX;
        int y = targetSceneY;

        while (x >= 0 && y >= 0)
        {
            route.addFirst(new int[]{ x, y });
            int prevX = search.previousX[x][y];
            int prevY = search.previousY[x][y];
            x = prevX;
            y = prevY;
        }

        if (route.isEmpty())
        {
            return null;
        }

        int waypointIndex = Math.min(route.size() - 1, Math.max(routeAdvance, 1));
        int currentIndex = 0;
        for (int[] step : route)
        {
            if (currentIndex++ != waypointIndex)
            {
                continue;
            }

            LocalPoint localPoint = LocalPoint.fromScene(step[0], step[1], client.getTopLevelWorldView());
            return localPoint != null ? WorldPoint.fromLocalInstance(client, localPoint, plane) : null;
        }

        return null;
    }

    private void updateMovementProgress(WorldPoint playerPos, int targetWorldX, int targetWorldY)
    {
        int currentDistance = worldDistance(playerPos.getX(), playerPos.getY(), targetWorldX, targetWorldY);
        boolean moved = lastPlayerPosition == null
            || !lastPlayerPosition.equals(playerPos);
        boolean progressed = currentDistance < lastDistanceToTarget;

        if (moved || progressed)
        {
            stagnantTicks = 0;
        }
        else
        {
            stagnantTicks++;
        }

        lastPlayerPosition = playerPos;
        lastDistanceToTarget = currentDistance;
    }

    private void resetProgress(TargetKey targetKey, WorldPoint playerPos, int targetWorldX, int targetWorldY)
    {
        activeTarget = targetKey;
        lastIssuedWaypoint = null;
        lastPlayerPosition = playerPos;
        lastDistanceToTarget = worldDistance(playerPos.getX(), playerPos.getY(), targetWorldX, targetWorldY);
        stagnantTicks = 0;
        lastProgressState = ProgressState.IDLE;
    }

    private static int worldDistance(int fromX, int fromY, int toX, int toY)
    {
        return Math.max(Math.abs(fromX - toX), Math.abs(fromY - toY));
    }

    private boolean tryPlaneTransition(WorldPoint playerPos, int targetPlane)
    {
        String[] actions = targetPlane > playerPos.getPlane() ? PLANE_UP_ACTIONS : PLANE_DOWN_ACTIONS;
        SceneObject obstacle = findBestObstacle(playerPos, playerPos.getX(), playerPos.getY(), playerPos.getPlane(),
            STUCK_OBSTACLE_SEARCH_RADIUS, actions);
        if (obstacle == null)
        {
            return false;
        }

        String action = firstMatchingAction(obstacle, actions);
        if (action == null)
        {
            return false;
        }

        log.info("PathfinderImpl: interacting with plane-transition obstacle '{}' via '{}' at ({},{},{})",
            obstacle.getName(), action, obstacle.getWorldX(), obstacle.getWorldY(), obstacle.getPlane());
        obstacle.interact(action);
        lastIssuedWaypoint = new WorldPoint(obstacle.getWorldX(), obstacle.getWorldY(), obstacle.getPlane());
        return true;
    }

    private boolean tryTraverseObstacle(WorldPoint playerPos, int targetWorldX, int targetWorldY, int plane, boolean stuck)
    {
        int searchRadius = stuck ? STUCK_OBSTACLE_SEARCH_RADIUS : OBSTACLE_SEARCH_RADIUS;
        SceneObject obstacle = findBestObstacle(playerPos, targetWorldX, targetWorldY, plane, searchRadius, TRAVERSE_ACTIONS);
        if (obstacle == null)
        {
            return false;
        }

        String action = firstMatchingAction(obstacle, TRAVERSE_ACTIONS);
        if (action == null)
        {
            return false;
        }

        log.info("PathfinderImpl: interacting with obstacle '{}' via '{}' at ({},{},{}) while routing to ({},{},{})",
            obstacle.getName(), action, obstacle.getWorldX(), obstacle.getWorldY(), obstacle.getPlane(),
            targetWorldX, targetWorldY, plane);
        obstacle.interact(action);
        lastIssuedWaypoint = new WorldPoint(obstacle.getWorldX(), obstacle.getWorldY(), obstacle.getPlane());
        return true;
    }

    private SceneObject findBestObstacle(
        WorldPoint playerPos,
        int targetWorldX,
        int targetWorldY,
        int plane,
        int searchRadius,
        String[] preferredActions)
    {
        List<SceneObject> candidates = gameObjects.all(o ->
            o.getPlane() == plane
                && worldDistance(playerPos.getX(), playerPos.getY(), o.getWorldX(), o.getWorldY()) <= searchRadius
                && hasAnyAction(o, preferredActions));

        return candidates.stream()
            .min(Comparator.comparingInt(o -> obstacleScore(playerPos, o, targetWorldX, targetWorldY)))
            .orElse(null);
    }

    private int obstacleScore(WorldPoint playerPos, SceneObject obstacle, int targetWorldX, int targetWorldY)
    {
        int playerDistance = worldDistance(playerPos.getX(), playerPos.getY(), obstacle.getWorldX(), obstacle.getWorldY());
        int targetDistance = worldDistance(obstacle.getWorldX(), obstacle.getWorldY(), targetWorldX, targetWorldY);
        int score = playerDistance * 100 + targetDistance * 10;

        if (lastIssuedWaypoint != null)
        {
            score += worldDistance(obstacle.getWorldX(), obstacle.getWorldY(),
                lastIssuedWaypoint.getX(), lastIssuedWaypoint.getY()) * 5;
        }

        return score;
    }

    private static boolean hasAnyAction(SceneObject obstacle, String[] preferredActions)
    {
        return firstMatchingAction(obstacle, preferredActions) != null;
    }

    private static String firstMatchingAction(SceneObject obstacle, String[] preferredActions)
    {
        for (String action : preferredActions)
        {
            if (obstacle.hasAction(action))
            {
                return action;
            }
        }
        return null;
    }

    private static final class SearchResult
    {
        private final boolean[][] visited;
        private final int[][]     distance;
        private final int[][]     previousX;
        private final int[][]     previousY;

        private SearchResult(boolean[][] visited, int[][] distance, int[][] previousX, int[][] previousY)
        {
            this.visited  = visited;
            this.distance = distance;
            this.previousX = previousX;
            this.previousY = previousY;
        }
    }

    private static final class TargetKey
    {
        private final int x;
        private final int y;
        private final int plane;

        private TargetKey(int x, int y, int plane)
        {
            this.x = x;
            this.y = y;
            this.plane = plane;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof TargetKey)) return false;
            TargetKey targetKey = (TargetKey) o;
            return x == targetKey.x && y == targetKey.y && plane == targetKey.plane;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(x, y, plane);
        }
    }
}
