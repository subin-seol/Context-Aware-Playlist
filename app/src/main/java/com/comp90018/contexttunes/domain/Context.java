package com.comp90018.contexttunes.domain;

import com.comp90018.contexttunes.data.sensors.LightSensor.LightBucket;
// timeofday is: "morning", "afternoon", "evening", "night"
// activity is something like: "still", "walking", "running" (mock for now)
public class Context {
    public final LightBucket lightLevel;
    public final String timeOfDay;
    public final String activity;

    public Context(LightBucket lightLevel, String timeOfDay, String activity) {
        this.lightLevel = lightLevel;
        this.timeOfDay = timeOfDay;
        this.activity = activity;
    }
}