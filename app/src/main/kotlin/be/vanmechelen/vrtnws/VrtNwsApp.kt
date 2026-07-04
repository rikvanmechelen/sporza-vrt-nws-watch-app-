package be.vanmechelen.vrtnws

import android.app.Application

class VrtNwsApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        instance = this
    }

    companion object {
        lateinit var instance: VrtNwsApp
            private set
    }
}
