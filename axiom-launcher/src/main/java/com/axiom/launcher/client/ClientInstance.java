package com.axiom.launcher.client;

import com.axiom.launcher.db.Account;

import java.time.Instant;

/**
 * Represents one running RuneLite client subprocess managed by {@link ClientManager}.
 *
 * Status values (kept as strings for easy display in the UI):
 *   RUNNING  — process is alive and the script is active
 *   BREAKING — script is in an antiban break
 *   PAUSED   — script paused (e.g. logged out)
 *   STOPPED  — process was stopped gracefully or by the user
 *   CRASHED  — process died unexpectedly (monitor thread detected this)
 *
 * Status transitions are driven by either the monitor thread (CRASHED) or
 * future IPC from the Axiom plugin inside the client (RUNNING→BREAKING etc.).
 */
public class ClientInstance
{
    private final int     instanceIndex;
    private final Account account;
    private final String  scriptName;
    private final int     world;
    private final Process process;
    private final Instant startedAt;

    /** Volatile: written by monitor thread, read by UI thread. */
    private volatile String status;

    public ClientInstance(
        int     instanceIndex,
        Account account,
        String  scriptName,
        int     world,
        Process process)
    {
        this.instanceIndex = instanceIndex;
        this.account       = account;
        this.scriptName    = scriptName;
        this.world         = world;
        this.process       = process;
        this.startedAt     = Instant.now();
        this.status        = "RUNNING";
    }

    // ── Control ───────────────────────────────────────────────────────────────

    /** Returns true while the OS process is alive. */
    public boolean isAlive()
    {
        return process.isAlive();
    }

    /**
     * Forcibly terminates the process and sets status to STOPPED.
     * Prefer graceful shutdown via the Axiom plugin IPC when available.
     */
    public void kill()
    {
        process.destroyForcibly();
        status = "STOPPED";
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /** Zero-based index assigned by ClientManager; used for window tiling. */
    public int     getInstanceIndex() { return instanceIndex; }
    public Account getAccount()       { return account; }
    public String  getScriptName()    { return scriptName; }
    public int     getWorld()         { return world; }
    public Process getProcess()       { return process; }
    public Instant getStartedAt()     { return startedAt; }
    public String  getStatus()        { return status; }

    /** Called by the monitor thread when the process dies unexpectedly. */
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString()
    {
        return String.format("ClientInstance{#%d account='%s' script='%s' world=%d status=%s}",
            instanceIndex,
            account != null ? account.displayName : "?",
            scriptName,
            world,
            status);
    }
}
