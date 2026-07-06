package me.vanmechelen.vrtsporza.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import me.vanmechelen.vrtsporza.VrtNwsApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as VrtNwsApp).graph
        // Deep-link: the sports Tile passes tab=matches to open straight on the Matches page.
        val initialTab = if (intent?.getStringExtra(EXTRA_TAB) == TAB_MATCHES) MATCHES_TAB_INDEX else 0
        setContent {
            AppRoot(
                repository = graph.repository,
                matchesRepository = graph.matchesRepository,
                initialTab = initialTab,
            )
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_MATCHES = "matches"
    }
}
