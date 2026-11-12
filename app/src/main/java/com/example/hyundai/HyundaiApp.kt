package com.example.hyundai

import android.app.Application
import android.content.Context
import com.example.hyundai.data.AppContainer
import com.example.hyundai.data.calendar.CalendarSyncWorker

class HyundaiApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CalendarSyncWorker.schedule(this)
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as HyundaiApp).container
