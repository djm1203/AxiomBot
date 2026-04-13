package com.axiom.api.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProgressionTest
{
    // ── parse + getMethodForLevel ─────────────────────────────────────────────

    @Test
    void returnsFirstMethodWhenLevelBelowAllThresholds()
    {
        Progression p = Progression.parse("30:Willow,60:Yew");
        assertEquals("Willow", p.getMethodForLevel(1),
            "Level below first threshold should return first entry's method");
        assertEquals("Willow", p.getMethodForLevel(29));
    }

    @Test
    void returnsCorrectMethodAtExactThreshold()
    {
        Progression p = Progression.parse("1:Normal,30:Willow,60:Yew");
        assertEquals("Normal",  p.getMethodForLevel(1));
        assertEquals("Willow",  p.getMethodForLevel(30));
        assertEquals("Yew",     p.getMethodForLevel(60));
    }

    @Test
    void returnsHighestMatchingMethodBelowLevel()
    {
        Progression p = Progression.parse("1:Normal,30:Willow,60:Yew");
        assertEquals("Willow", p.getMethodForLevel(45));
        assertEquals("Yew",    p.getMethodForLevel(75));
        assertEquals("Yew",    p.getMethodForLevel(99));
    }

    @Test
    void defaultWoodcuttingProgressionParsesCorrectly()
    {
        Progression p = Progression.parse(Progression.DEFAULT_WOODCUTTING);
        assertFalse(p.isEmpty());
        assertEquals("Oak",    p.getMethodForLevel(1));
        assertEquals("Oak",    p.getMethodForLevel(29));
        assertEquals("Willow", p.getMethodForLevel(30));
        assertEquals("Willow", p.getMethodForLevel(44));
        assertEquals("Maple",  p.getMethodForLevel(45));
        assertEquals("Yew",    p.getMethodForLevel(60));
        assertEquals("Magic",  p.getMethodForLevel(75));
        assertEquals("Magic",  p.getMethodForLevel(99));
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    @Test
    void returnsNullFallbackWhenEmptyAndNoFallback()
    {
        Progression p = Progression.parse("");
        assertTrue(p.isEmpty());
        assertNull(p.getMethodForLevel(50));
    }

    @Test
    void returnsFallbackWhenEmpty()
    {
        Progression p = Progression.parse("", "Oak");
        assertEquals("Oak", p.getMethodForLevel(1));
        assertEquals("Oak", p.getMethodForLevel(99));
    }

    @Test
    void returnsFallbackWhenNullInput()
    {
        Progression p = Progression.parse(null, "Normal");
        assertEquals("Normal", p.getMethodForLevel(50));
    }

    // ── Malformed input ───────────────────────────────────────────────────────

    @Test
    void skipsMalformedTokens()
    {
        // "bad", ":Oak", "30:" are all malformed and skipped
        Progression p = Progression.parse("bad,1:Normal,:Oak,30:");
        assertFalse(p.isEmpty(), "Only malformed entries skipped; 1:Normal is valid");
        assertEquals("Normal", p.getMethodForLevel(1));
    }

    @Test
    void emptyWhenAllMalformed()
    {
        Progression p = Progression.parse("bad,alsobad");
        assertTrue(p.isEmpty());
    }

    @Test
    void skipsOutOfRangeLevels()
    {
        // Level 0 and 100 are outside [1,99]
        Progression p = Progression.parse("0:Invalid,1:Normal,100:TooHigh");
        List<Progression.Entry> entries = p.getEntries();
        assertEquals(1, entries.size());
        assertEquals("Normal", entries.get(0).method);
    }

    // ── Ordering ──────────────────────────────────────────────────────────────

    @Test
    void sortsEntriesAscending()
    {
        // Intentionally reversed input order
        Progression p = Progression.parse("60:Yew,1:Normal,30:Willow");
        List<Progression.Entry> entries = p.getEntries();
        assertEquals(1,  entries.get(0).level);
        assertEquals(30, entries.get(1).level);
        assertEquals(60, entries.get(2).level);
    }

    @Test
    void worksWithExtraWhitespace()
    {
        Progression p = Progression.parse(" 1 : Normal , 30 : Willow ");
        assertEquals("Normal", p.getMethodForLevel(1));
        assertEquals("Willow", p.getMethodForLevel(30));
    }

    // ── isEmpty ───────────────────────────────────────────────────────────────

    @Test
    void isEmptyForBlankString()
    {
        assertTrue(Progression.parse("").isEmpty());
        assertTrue(Progression.parse("   ").isEmpty());
        assertTrue(Progression.parse(null).isEmpty());
    }

    @Test
    void isNotEmptyForValidInput()
    {
        assertFalse(Progression.parse("1:Oak").isEmpty());
    }
}
