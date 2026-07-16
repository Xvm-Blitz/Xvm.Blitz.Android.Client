package ru.xvmblitz.android

import android.app.Application
import ru.xvmblitz.android.data.AppContainer

class XvmBlitzApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
    }

    companion object {
        lateinit var instance: XvmBlitzApp
            private set
    }
}
