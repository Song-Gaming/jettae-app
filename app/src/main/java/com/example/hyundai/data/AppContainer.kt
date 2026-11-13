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
import com.example.hyundai.data.remote.ServerModeStore
import com.example.hyundai.data.remote.SwitchingServerApi
import com.example.hyundai.data.token.DeviceTokenStore
import java.time.Clock

/**
 * 앱 전체에서 하나씩만 쓰는 객체들을 만든다.
 * 실제 서버와 가짜 일정 시나리오는 일정 탭에서 고른다. SERVER_URL(환경변수 또는 local.properties)이
 * 비어 있으면 가짜 일정만 고를 수 있다.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val serverMode = ServerModeStore(appContext, hasRealServer = BuildConfig.SERVER_URL.isNotBlank())

    private val realServerApi: ServerApi? =
        if (serverMode.hasRealServer) HttpServerApi(BuildConfig.SERVER_URL, DeviceTokenStore(appContext)) else null

    private val fakeServerApi = FakeServerApi(scenario = { serverMode.mode })

    val serverApi: ServerApi = SwitchingServerApi { realServerApi?.takeUnless { serverMode.isFake } ?: fakeServerApi }

    /** 0 = 실제 서버, 1~ = 가짜 일정 시나리오. 가짜 일정을 고르면 그 캘린더에 연동된 것으로 본다 */
    fun chooseServerMode(mode: Int) {
        serverMode.mode = mode
        if (mode != ServerModeStore.REAL) fakeServerApi.start()
    }

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
