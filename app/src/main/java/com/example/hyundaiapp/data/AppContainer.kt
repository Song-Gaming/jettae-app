package com.example.hyundaiapp.data

import android.content.Context
import com.example.hyundaiapp.BuildConfig
import com.example.hyundaiapp.data.calendar.CalendarRepository
import com.example.hyundaiapp.data.calendar.PrefsLastSyncStore
import com.example.hyundaiapp.data.local.AppDatabase
import com.example.hyundaiapp.data.remote.FakeServerApi
import com.example.hyundaiapp.data.remote.HttpServerApi
import com.example.hyundaiapp.data.remote.ServerApi
import com.example.hyundaiapp.data.token.DeviceTokenStore
import java.time.Clock

/**
 * 앱 전체에서 하나씩만 쓰는 객체들을 만든다.
 * local.properties의 SERVER_BASE_URL이 비어 있으면 가짜 서버를 쓴다.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val isFakeServer: Boolean = BuildConfig.SERVER_BASE_URL.isBlank()

    val serverApi: ServerApi =
        if (isFakeServer) FakeServerApi() else HttpServerApi(BuildConfig.SERVER_BASE_URL, DeviceTokenStore(appContext))

    private val database by lazy { AppDatabase.build(appContext) }

    val calendarRepository by lazy {
        CalendarRepository(
            api = serverApi,
            dao = database.calendarEventDao(),
            lastSync = PrefsLastSyncStore(appContext),
            clock = Clock.systemDefaultZone(),
        )
    }
}
