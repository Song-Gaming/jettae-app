package com.example.hyundaiapp

import android.app.Application
import android.content.Context
import com.example.hyundaiapp.data.AppContainer
import com.example.hyundaiapp.data.calendar.CalendarSyncWorker

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
