package com.comp90018.contexttunes.domain;

import com.comp90018.contexttunes.data.sensors.LightSensor.LightBucket;
import com.comp90018.contexttunes.data.weather.WeatherService.WeatherState;

// timeofday is: "morning", "afternoon", "evening", "night"
// activity is something like: "still", "walking", "running" (mock for now)
// weather is: SUNNY, CLOUDY, RAINY, UNKNOWN
public class Context {
    public final LightBucket lightLevel;
    public final String timeOfDay;
    public final String activity;
    public final WeatherState weather;

    public Context(LightBucket lightLevel, String timeOfDay, String activity, WeatherState weather) {
        this.lightLevel = lightLevel;
        this.timeOfDay = timeOfDay;
        this.activity = activity;
        this.weather = weather;
    }
}