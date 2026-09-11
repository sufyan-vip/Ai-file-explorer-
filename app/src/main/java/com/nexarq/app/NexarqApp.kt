package com.nexarq.app

import android.app.Application
import com.nexarq.app.data.AppContainer

class NexarqApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
