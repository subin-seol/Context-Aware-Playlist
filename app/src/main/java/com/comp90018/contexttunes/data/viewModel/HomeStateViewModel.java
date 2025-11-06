package com.comp90018.contexttunes.data.viewModel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.contexttunes.data.weather.WeatherService;
import com.comp90018.contexttunes.domain.SpotifyPlaylist;

import java.util.ArrayList;
import java.util.List;

public class HomeStateViewModel extends ViewModel {
    private final MutableLiveData<List<SpotifyPlaylist>> playlists = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> recommendationsGenerated = new MutableLiveData<>(false);
    private MutableLiveData<WeatherService.WeatherState> weatherState = new MutableLiveData<>();

    public LiveData<List<SpotifyPlaylist>> getPlaylists() {
        return playlists;
    }

    public void setPlaylists(List<SpotifyPlaylist> value) {
        playlists.setValue(value);
    }

    public LiveData<Boolean> getRecommendationsGenerated() {
        return recommendationsGenerated;
    }

    public void setRecommendationsGenerated(boolean value) {
        recommendationsGenerated.setValue(value);
    }

    public LiveData<WeatherService.WeatherState> getWeatherState() {
        return weatherState;
    }

    public void setWeatherState(WeatherService.WeatherState weather) {
        weatherState.setValue(weather);
    }
}