package com.botengine.osrs.license;

/** Access tiers for Axiom scripts. */
public enum LicenseTier
{
    /** Default — F2P scripts only. */
    FREE,

    /** Paid — F2P + P2P scripts, time-limited. */
    PREMIUM,

    /**
     * Creator — all scripts, never expires.
     * Granted by the hardcoded CREATOR_KEY in {@link LicenseManager}.
     */
    CREATOR
}
