package com.comp90018.contexttunes.utils;

public final class AppEvents {
    private AppEvents() {}

    // Broadcast: fired when a speed measurement is ready
    public static final String ACTION_SPEED_UPDATE =
            "com.comp90018.contexttunes.ACTION_SPEED_UPDATE";

    // Command: ask the service to run a 5s on-demand burst
    public static final String ACTION_SPEED_SAMPLE_NOW =
            "com.comp90018.contexttunes.ACTION_SPEED_SAMPLE_NOW";

    // Extras carried with ACTION_SPEED_UPDATE
    public static final String EXTRA_SPEED_MPS   = "extra_speed_mps";    // float (m/s)
    public static final String EXTRA_SPEED_KMH   = "extra_speed_kmh";    // float (km/h)
    public static final String EXTRA_CADENCE_SPM = "extra_cadence_spm";  // float (steps/min)
    public static final String EXTRA_ACTIVITY    = "extra_activity";     // "still|walking|running"
    public static final String EXTRA_TARGET_BPM  = "extra_target_bpm";   // int 60..190
    public static final String EXTRA_SOURCE      = "extra_source";       // "gps|steps"

    // 4-way tag used by UI: STILL | WALK | RUN | WHEELS
    public static final String EXTRA_SPEED_TAG   = "extra_speed_tag";    // String
}
