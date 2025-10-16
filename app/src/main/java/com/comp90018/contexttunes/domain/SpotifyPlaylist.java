package com.comp90018.contexttunes.domain;

public class SpotifyPlaylist {
    public final String id;
    public final String name;
    public final String description;
    public final String imageUrl;
    public final String ownerName;
    public final int totalTracks;
    public final String externalUrl;

    public SpotifyPlaylist(
            String id,
            String name,
            String description,
            String imageUrl,
            String ownerName,
            int totalTracks,
            String externalUrl
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.ownerName = ownerName;
        this.totalTracks = totalTracks;
        this.externalUrl = externalUrl;
    }
}
