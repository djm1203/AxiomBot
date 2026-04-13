package com.botengine.osrs.script;

/**
 * Represents the current execution state of the active bot script.
 *
 * State transitions:
 *
 *   STOPPED ──► RUNNING ──► PAUSED
 *      ▲           │           │
 *      │           ▼           │
 *      │        BREAKING ──────┘
 *      │           │
 *      └───────────┘
 *
 * STOPPED  - No script is running. ScriptRunner does nothing on game tick.
 * RUNNING  - Active script's onLoop() is called every game tick.
 * PAUSED   - Script is loaded but onLoop() is not called. State is preserved.
 * BREAKING - Antiban-triggered break. Resumes to RUNNING after break duration.
 */
public enum ScriptState
{
    STOPPED,
    RUNNING,
    PAUSED,
    BREAKING
}
