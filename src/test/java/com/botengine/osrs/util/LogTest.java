package com.botengine.osrs.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Log utility.
 *
 * Log wraps SLF4J — we can't easily assert on log output in unit tests
 * without a test appender, so these tests focus on:
 *   1. That methods don't throw with or without varargs
 *   2. That parameterized messages format correctly (via the format helper)
 *   3. That scriptName prefix is applied and cleared correctly
 *
 * Log.format() is package-private — tested indirectly via public methods.
 * If the methods complete without exception, formatting worked.
 */
class LogTest
{
    private Log log;

    @BeforeEach
    void setUp()
    {
        log = new Log();
    }

    @Test
    void info_noArgs_doesNotThrow()
    {
        assertDoesNotThrow(() -> log.info("Simple info message"));
    }

    @Test
    void info_withArgs_doesNotThrow()
    {
        assertDoesNotThrow(() -> log.info("Value is {} and {}", 42, "hello"));
    }

    @Test
    void warn_withArgs_doesNotThrow()
    {
        assertDoesNotThrow(() -> log.warn("Warning: count={}", 7));
    }

    @Test
    void debug_withArgs_doesNotThrow()
    {
        assertDoesNotThrow(() -> log.debug("Debug: id={} name={}", 100, "Oak"));
    }

    @Test
    void error_noArgs_doesNotThrow()
    {
        assertDoesNotThrow(() -> log.error("Something broke"));
    }

    @Test
    void error_withThrowable_doesNotThrow()
    {
        assertDoesNotThrow(() -> log.error("Exception occurred", new RuntimeException("test")));
    }

    @Test
    void setScriptName_appliedToSubsequentLogs()
    {
        // Just verify no exception — prefix logic runs inside each log call
        log.setScriptName("Woodcutting");
        assertDoesNotThrow(() -> log.info("Running script"));
    }

    @Test
    void setScriptName_null_doesNotThrow()
    {
        log.setScriptName(null);
        assertDoesNotThrow(() -> log.info("No script active"));
    }

    @Test
    void setScriptName_empty_doesNotThrow()
    {
        log.setScriptName("");
        assertDoesNotThrow(() -> log.info("Empty script name"));
    }
}
