package com.comp90018.contexttunes.utils;

/**
 * Central place to keep broadcast actions and extras for speed sensing.
 */
public final class AppEvents {
    private AppEvents() {}

    /** Broadcast sent whenever a new speed estimation is available. */
    public static final String ACTION_SPEED_UPDATE =
            "com.comp90018.contexttunes.ACTION_SPEED_UPDATE";

    /** Optional command to ask the service to perform an immediate 5s burst. */
    public static final String ACTION_SPEED_SAMPLE_NOW =
            "com.comp90018.contexttunes.ACTION_SPEED_SAMPLE_NOW";

    /** Extras carried with ACTION_SPEED_UPDATE. */
    public static final String EXTRA_SPEED_MPS   = "extra_speed_mps";   // float (m/s)
    public static final String EXTRA_SPEED_KMH   = "extra_speed_kmh";   // float (km/h)
    public static final String EXTRA_CADENCE_SPM = "extra_cadence_spm"; // float (steps/min)
    public static final String EXTRA_ACTIVITY    = "extra_activity";    // String: still|walking|running
    public static final String EXTRA_TARGET_BPM  = "extra_target_bpm";  // int: recommended BPM
    public static final String EXTRA_SOURCE      = "extra_source";      // String: gps|steps
}
