package com.musit.player.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.musit.player.ui.MainViewModel
import com.musit.player.ui.components.MiniPlayer
import com.musit.player.ui.components.PlaylistPickerDialog
import com.musit.player.ui.home.HomeScreen
import com.musit.player.ui.library.LibraryScreen
import com.musit.player.ui.library.PlaylistDetailScreen
import com.musit.player.ui.player.PlayerScreen
import com.musit.player.ui.queue.QueueScreen
import com.musit.player.ui.search.SearchScreen
import com.musit.player.ui.settings.SettingsScreen

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val SEARCH = "search?query={query}"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val QUEUE = "queue"
    const val PLAYLIST = "playlist/{playlistId}"
    const val ARTIST = "artist/{artistName}"
    const val EQUALIZER = "equalizer"

    fun playlist(playlistId: Long) = "playlist/$playlistId"
    fun search(query: String) = "search?query=$query"
    fun artist(artistName: String) = "artist/$artistName"
}

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, "Home", Icons.Filled.Home),
    BottomTab(Routes.SEARCH, "Search", Icons.Filled.Search),
    BottomTab(Routes.LIBRARY, "Library", Icons.Filled.LibraryMusic),
    BottomTab(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
)

@Composable
fun MusitNavGraph(
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomTabs.map { it.route }

    val playback = mainViewModel.playback
    val currentSong by playback.currentSong.collectAsStateWithLifecycle()
    val isPlaying by playback.isPlaying.collectAsStateWithLifecycle()
    val isLoading by playback.isLoading.collectAsStateWithLifecycle()
    val positionMs by playback.positionMs.collectAsStateWithLifecycle()
    val durationMs by playback.durationMs.collectAsStateWithLifecycle()

    val pendingPlaylistSong by mainViewModel.pendingPlaylistSong.collectAsStateWithLifecycle()
    val playlists by mainViewModel.playlists.collectAsStateWithLifecycle()

    fun playAndOpenPlayer(songs: List<com.musit.player.model.Song>, index: Int) {
        mainViewModel.playQueue(songs, index)
        navController.navigate(Routes.PLAYER)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val song = currentSong
                    if (song != null) {
                        val progress = if (durationMs > 0) {
                            positionMs.toFloat() / durationMs.toFloat()
                        } else 0f
                        MiniPlayer(
                            song = song,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            progress = progress,
                            onTogglePlayPause = { playback.togglePlayPause() },
                            onTap = { navController.navigate(Routes.PLAYER) },
                            onNext = { playback.next() },
                            onPrevious = { playback.previous() }
                        )
                    }
                    NavigationBar {
                        bottomTabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.HOME)
                                        launchSingleTop = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.SPLASH) {
                com.musit.player.ui.splash.SplashScreen(
                    onDone = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onSongClick = ::playAndOpenPlayer,
                    onMoodClick = { mood ->
                        navController.navigate(Routes.search(mood))
                    },
                    onDownloadsClick = {
                        navController.navigate(Routes.LIBRARY)
                    },
                    onSettingsClick = {
                        navController.navigate(Routes.SETTINGS)
                    }
                )
            }
            composable(
                route = Routes.SEARCH,
                arguments = listOf(navArgument("query") {
                    type = NavType.StringType
                    defaultValue = ""
                })
            ) { entry ->
                val initialQuery = entry.arguments?.getString("query") ?: ""
                SearchScreen(
                    onSongClick = ::playAndOpenPlayer,
                    onPlayNext = mainViewModel::playNext,
                    onToggleFavorite = mainViewModel::toggleFavorite,
                    onAddToPlaylist = mainViewModel::requestAddToPlaylist,
                    initialQuery = initialQuery
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onSongClick = ::playAndOpenPlayer,
                    onPlayNext = mainViewModel::playNext,
                    onAddToPlaylist = mainViewModel::requestAddToPlaylist,
                    onOpenPlaylist = { id -> navController.navigate(Routes.playlist(id)) },
                    onSearchClick = { navController.navigate(Routes.SEARCH) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(snackbarHostState = snackbarHostState)
            }
            composable(Routes.PLAYER) {
                PlayerScreen(
                    onClose = { navController.popBackStack() },
                    onOpenQueue = { navController.navigate(Routes.QUEUE) },
                    onArtistClick = { artistName ->
                        navController.navigate(Routes.artist(artistName))
                    }
                )
            }
            composable(Routes.QUEUE) {
                QueueScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.PLAYLIST,
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
            ) { entry ->
                val playlistId = entry.arguments?.getLong("playlistId") ?: return@composable
                PlaylistDetailScreen(
                    playlistId = playlistId,
                    onBack = { navController.popBackStack() },
                    onSongClick = ::playAndOpenPlayer
                )
            }
            composable(
                route = Routes.ARTIST,
                arguments = listOf(navArgument("artistName") { type = NavType.StringType })
            ) { entry ->
                val artistName = entry.arguments?.getString("artistName") ?: return@composable
                com.musit.player.ui.artist.ArtistDetailScreen(
                    artistName = artistName,
                    onBack = { navController.popBackStack() },
                    onSongClick = ::playAndOpenPlayer
                )
            }
            composable(Routes.EQUALIZER) {
                com.musit.player.ui.equalizer.EqualizerScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    // Global "add to playlist" dialog, reachable from every song menu.
    val songToAdd = pendingPlaylistSong
    if (songToAdd != null) {
        PlaylistPickerDialog(
            playlists = playlists,
            onDismiss = mainViewModel::dismissPlaylistDialog,
            onSelect = mainViewModel::confirmAddToPlaylist,
            onCreateNew = mainViewModel::createPlaylistAndAdd
        )
    }
}
