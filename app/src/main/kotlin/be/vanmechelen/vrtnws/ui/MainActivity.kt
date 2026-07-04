package be.vanmechelen.vrtnws.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import be.vanmechelen.vrtnws.VrtNwsApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as VrtNwsApp).graph
        setContent {
            AppRoot(repository = graph.repository, matchesRepository = graph.matchesRepository)
        }
    }
}
