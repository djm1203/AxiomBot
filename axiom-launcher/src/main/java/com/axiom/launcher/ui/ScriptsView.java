package com.axiom.launcher.ui;

import com.axiom.launcher.client.ClientManager;
import com.axiom.launcher.db.AccountRepository;
import com.axiom.launcher.db.ProxyRepository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Scripts panel — tile grid of available scripts with category filter chips.
 * Clicking a card opens LaunchDialog pre-filled with that script.
 */
public class ScriptsView extends VBox
{
    private final ClientManager     clientManager;
    private final AccountRepository accountRepo;
    private final ProxyRepository   proxyRepo;
    private final Stage             owner;
    private final Runnable          switchToSessions;
    private final Supplier<List<ScriptInfo>> scriptLoader;

    private final TilePane tilePane = new TilePane();

    private List<ScriptInfo> allScripts   = new ArrayList<>();
    private String           activeFilter = "ALL";

    public ScriptsView(Supplier<List<ScriptInfo>> scriptLoader,
                       ClientManager     clientManager,
                       AccountRepository accountRepo,
                       ProxyRepository   proxyRepo,
                       Stage             owner,
                       Runnable          switchToSessions)
    {
        this.scriptLoader     = scriptLoader;
        this.clientManager    = clientManager;
        this.accountRepo      = accountRepo;
        this.proxyRepo        = proxyRepo;
        this.owner            = owner;
        this.switchToSessions = switchToSessions;

        getStyleClass().add("content-pane");
        setSpacing(16);
        setPadding(new Insets(24));

        buildHeader();
        buildTileGrid();

        allScripts = scriptLoader.get();
        applyFilter();
    }

    /** Reloads the script list from the configured scripts dir and rebuilds the grid. */
    public void refresh()
    {
        allScripts = scriptLoader.get();
        applyFilter();
    }

    // ── UI ─────────────────────────────────────────────────────────────────────

    private void buildHeader()
    {
        Label title    = new Label("Scripts");
        Label subtitle = new Label("Click a script card to launch a new client.");
        title.getStyleClass().add("panel-title");
        subtitle.getStyleClass().add("panel-subtitle");

        HBox chipBar = buildFilterBar();

        getChildren().addAll(title, subtitle, new Separator(), chipBar);
    }

    private HBox buildFilterBar()
    {
        String[]     categories = {"ALL", "SKILLING", "COMBAT", "MONEY"};
        ToggleGroup  group      = new ToggleGroup();
        HBox         bar        = new HBox(6);
        bar.setAlignment(Pos.CENTER_LEFT);

        for (String cat : categories)
        {
            ToggleButton chip = new ToggleButton(cat);
            chip.getStyleClass().add("filter-chip");
            chip.setToggleGroup(group);
            chip.setSelected("ALL".equals(cat));
            chip.selectedProperty().addListener((obs, old, now) ->
            {
                if (Boolean.TRUE.equals(now)) { activeFilter = cat; applyFilter(); }
            });
            bar.getChildren().add(chip);
        }

        return bar;
    }

    private void buildTileGrid()
    {
        tilePane.setHgap(12);
        tilePane.setVgap(12);
        tilePane.setPrefTileWidth(210);
        tilePane.setPrefTileHeight(126);

        ScrollPane scroll = new ScrollPane(tilePane);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);
    }

    private void applyFilter()
    {
        List<ScriptInfo> visible = "ALL".equals(activeFilter)
            ? new ArrayList<>(allScripts)
            : allScripts.stream()
                        .filter(s -> activeFilter.equalsIgnoreCase(s.category))
                        .collect(Collectors.toList());

        tilePane.getChildren().clear();

        if (visible.isEmpty())
        {
            Label empty = new Label("No scripts match this filter.");
            empty.getStyleClass().add("label-muted");
            tilePane.getChildren().add(empty);
            return;
        }

        for (ScriptInfo s : visible) tilePane.getChildren().add(buildCard(s));
    }

    private VBox buildCard(ScriptInfo s)
    {
        Label icon = new Label(s.emoji);
        icon.getStyleClass().add("script-icon");

        Label name = new Label(s.name);
        name.getStyleClass().add("script-name");
        name.setWrapText(true);

        Label cat = new Label(s.category);
        cat.getStyleClass().add(catStyle(s.category));

        Label desc = new Label(s.description);
        desc.getStyleClass().add("label-muted");
        desc.setWrapText(true);

        HBox nameRow = new HBox(8, name, cat);
        nameRow.setAlignment(Pos.BOTTOM_LEFT);

        HBox topRow = new HBox(10, icon, nameRow);
        topRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, topRow, desc);
        card.getStyleClass().add("script-card");
        card.setMaxWidth(Double.MAX_VALUE);

        card.setOnMouseClicked(e -> openLaunchDialog(s));
        return card;
    }

    private void openLaunchDialog(ScriptInfo preselected)
    {
        new LaunchDialog(owner, clientManager, accountRepo, proxyRepo, allScripts, preselected)
            .showAndWait()
            .ifPresent(instance ->
            {
                if (switchToSessions != null) switchToSessions.run();
            });
    }

    private static String catStyle(String category)
    {
        if (category == null) return "cat-skilling";
        switch (category.toUpperCase())
        {
            case "COMBAT": return "cat-combat";
            case "MONEY":  return "cat-money";
            default:       return "cat-skilling";
        }
    }
}
