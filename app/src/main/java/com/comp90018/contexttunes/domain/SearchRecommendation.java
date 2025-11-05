package com.comp90018.contexttunes.domain;

/**
 * Output of the AI stage for Spotify search.
 * - searchQuery: the string you will pass to the Spotify search endpoint
 * - reason: a short human-readable explanation from the AI for debugging/transparency
 */
public class SearchRecommendation {
    public final String searchQuery;
    public final String reason;

    public SearchRecommendation(String searchQuery, String reason) {
        this.searchQuery = searchQuery;
        this.reason = reason;
    }
}