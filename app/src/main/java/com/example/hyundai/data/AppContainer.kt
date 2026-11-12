package com.example.hyundai.data

import android.content.Context
import com.example.hyundai.BuildConfig
import com.example.hyundai.data.calendar.CalendarRepository
import com.example.hyundai.data.calendar.PrefsLastSyncStore
import com.example.hyundai.data.local.AppDatabase
import com.example.hyundai.data.places.PlaceRepository
import com.example.hyundai.data.remote.FakeServerApi
import com.example.hyundai.data.remote.HttpServerApi
import com.example.hyundai.data.remote.ServerApi
import com.example.hyundai.data.token.DeviceTokenStore
import java.time.Clock

/**
 * 앱 전체에서 하나씩만 쓰는 객체들을 만든다.
 * SERVER_URL(환경변수 또는 local.properties)이 비어 있으면 가짜 서버를 쓴다.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val isFakeServer: Boolean = BuildConfig.SERVER_URL.isBlank()

    val serverApi: ServerApi =
        if (isFakeServer) FakeServerApi() else HttpServerApi(BuildConfig.SERVER_URL, DeviceTokenStore(appContext))

    private val database by lazy { AppDatabase.build(appContext) }

    /** A가 도착·출발 이벤트 때 쓰는 [com.example.hyundai.data.places.VisitRecorder]이기도 하다 */
    val placeRepository by lazy {
        PlaceRepository(
            visitDao = database.visitDao(),
            labelDao = database.placeLabelDao(),
            clock = Clock.systemDefaultZone(),
        )
    }

    val calendarRepository by lazy {
        CalendarRepository(
            api = serverApi,
            dao = database.calendarEventDao(),
            lastSync = PrefsLastSyncStore(appContext),
            clock = Clock.systemDefaultZone(),
        )
    }
}
