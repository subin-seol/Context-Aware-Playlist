package com.comp90018.contexttunes.domain;

public class Playlist {
    public final String name;
    public final String spotifyUri;
    public final String webUrl;

    public Playlist(String name, String spotifyUri, String webUrl) {
        this.name = name;
        this.spotifyUri = spotifyUri;
        this.webUrl = webUrl;
    }
}