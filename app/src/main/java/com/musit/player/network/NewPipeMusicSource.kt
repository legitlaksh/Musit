package com.musit.player.network

import com.musit.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube Music source via the NewPipe Extractor library (InnerTube-compatible).
 *
 * This implementation ONLY retrieves publicly available metadata and thumbnails.
 * It does NOT extract direct stream URLs or bypass any access controls.
 * Playback uses YouTube's supported embed player (WebView), not raw URLs.
 *
 * The YouTube source is completely separated from the UI and Media3 player —
 * it only provides [Song] metadata through the [MusicSource] interface.
 */
@Singleton
class NewPipeMusicSource @Inject constructor() : MusicSource {

    private val youtubeService = ServiceList.YouTube

    init {
        // Initialize NewPipe with default settings
        NewPipe.init(
            object : org.schabi.newpipe.extractor.downloader.Downloader() {
                private val client = okhttp3.OkHttpClient()

                override fun execute(request: org.schabi.newpipe.extractor.downloader.Request):
                        org.schabi.newpipe.extractor.downloader.Response {
                    val reqBuilder = okhttp3.Request.Builder()
                        .url(request.url())
                        .header("User-Agent", request.headers()["User-Agent"]?.firstOrNull()
                            ?: "Mozilla/5.0")
                    request.headers().forEach { (k, v) ->
                        v.forEach { reqBuilder.header(k, it) }
                    }
                    request.dataToSend()?.let {
                        reqBuilder.post(
                            it.toRequestBody("application/octet-stream".toMediaType())
                        )
                    }
                    val response = client.newCall(reqBuilder.build()).execute()
                    return org.schabi.newpipe.extractor.downloader.Response(
                        response.code,
                        response.message,
                        response.headers.toMultimap(),
                        response.body?.string(),
                        response.request.url.toString()
                    )
                }
            }
        )
    }

    override suspend fun search(query: String): Result<List<Song>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val searchInfo = SearchInfo.getInfo(
                    youtubeService,
                    youtubeService.searchQHFactory.fromQuery(query)
                )
                val songs = mutableListOf<Song>()
                for (item in searchInfo.relatedItems) {
                    if (item is StreamInfoItem) {
                        songs.add(
                            Song(
                                id = extractVideoId(item.url),
                                title = item.name,
                                artist = item.uploaderName ?: "Unknown",
                                artworkUrl = item.thumbnails.firstOrNull()?.url,
                                durationMs = item.duration * 1000L
                            )
                        )
                    }
                    if (songs.size >= 20) break
                }
                songs.ifEmpty { throw IllegalStateException("No results") }
            }
        }

    override suspend fun getSearchSuggestions(query: String): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val service = youtubeService
                val suggestions = service.suggestionExtractor
                    ?.suggestionList(query)
                    ?: emptyList()
                suggestions.take(8)
            } catch (e: Exception) {
                emptyList()
            }
        }

    override suspend fun getStreamUrl(songId: String): Result<String> {
        // NOT SUPPORTED: We do not extract direct stream URLs.
        // Playback uses YouTube's official embed player via WebView.
        // The videoId (songId) is used directly by the WebView player.
        return Result.failure(
            UnsupportedOperationException(
                "Direct stream URLs are not used. Playback via supported YouTube embed player."
            )
        )
    }

    override suspend fun getStreamUrls(songId: String): Result<List<String>> {
        return Result.failure(
            UnsupportedOperationException(
                "Direct stream URLs are not used. Playback via supported YouTube embed player."
            )
        )
    }

    override suspend fun getTrending(): Result<List<Song>> =
        withContext(Dispatchers.IO) {
            search("trending music india").recoverCatching {
                search("top songs").getOrThrow()
            }
        }

    private fun extractVideoId(url: String): String {
        // URL is like https://www.youtube.com/watch?v=VIDEO_ID
        return url.substringAfter("v=").substringBefore("&")
    }

    private fun String.toMediaType(): okhttp3.MediaType =
        this.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()!!

    private fun String.toMediaTypeOrNull(): okhttp3.MediaType? =
        try {
            okhttp3.MediaType.get(this)
        } catch (e: Exception) {
            null
        }

    private fun ByteArray.toRequestBody(mediaType: okhttp3.MediaType): okhttp3.RequestBody =
        okhttp3.RequestBody.create(mediaType, this)
}
