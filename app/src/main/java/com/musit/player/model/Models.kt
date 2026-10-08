package com.musit.player.model

/**
 * Domain models. The network layer ([com.musit.player.network.MusicSource])
 * produces these; the UI and player consume them. Local-only fields
 * (favorites, recents) live in the database layer, not here.
 */
data class Song(
    val id: String, // YouTube videoId — the stable identity
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationText: String? = null,
    val durationSec: Long? = null
)

data class Artist(
    val id: String,
    val name: String,
    val artworkUrl: String? = null
)

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val songs: List<Song> = emptyList()
)

data class Playlist(
    val id: String,
    val title: String,
    val artworkUrl: String? = null,
    val songs: List<Song> = emptyList()
)

/** Queue state exposed by the player. */
data class QueueState(
    val current: Song?,
    val upcoming: List<Song>,
    val currentIndex: Int
)
