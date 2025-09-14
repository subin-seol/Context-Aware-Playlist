package com.comp90018.contexttunes.domain;

public class Recommendation {
    public final Playlist playlist;
    public final String reason;

    public Recommendation(Playlist playlist, String reason) {
        this.playlist = playlist;
        this.reason = reason;
    }
}