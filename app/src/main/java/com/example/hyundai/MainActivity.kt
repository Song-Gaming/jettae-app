package com.example.hyundai

import ai.pleos.playground.navi.constants.BookmarkType
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.RangeRemainingListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.hyundai.data.local.VisitSource
import com.example.hyundai.data.places.Arrival
import com.example.hyundai.data.places.PersonalMatch
import com.example.hyundai.ui.calendar.CalendarPage
import com.example.hyundai.ui.places.PlacesPage
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.ZonedDateTime

private const val GLEO_PACKAGE = "ai.umos.ambientai"
private const val SHELL_PACKAGE = "com.android.shell"

/**
 * Only Gleo may start a route. The referrer the system fills in is the caller's package,
 * but getReferrer() prefers EXTRA_REFERRER(_NAME), which any caller can set, so those intents are refused.
 * ponytail: debug builds also accept adb shell so `am start` tests keep working.
 */
internal fun isGleoCaller(referrerHost: String?, hasReferrerExtra: Boolean, allowShell: Boolean): Boolean =
    !hasReferrerExtra && (referrerHost == GLEO_PACKAGE || (allowShell && referrerHost == SHELL_PACKAGE))

/**
 * Gleo Action(NAVIGATE_TO_PLACE) 테스트.
 * 후보(자주 방문 장소 + 내비 즐겨찾기 중 이름이 맞는 곳)가 여럿이면 asking으로 선택 요청 -> selectedPlace로 재호출.
 * 후보가 하나면 asking으로 확인 요청 -> confirmed=true로 재호출.
 * 목적지가 정해지면 NaviHelper 경로 요청 -> Callback.
 */
class MainActivity : AppCompatActivity() {

    private data class Candidate(val source: String, val route: RouteInfo)

    private lateinit var naviHelper: NaviHelper
    private lateinit var vehicle: Vehicle
    private val vehicleUnsubscribers = mutableListOf<() -> Unit>()
    private var car: Car? = null
    private var lastGear: VehicleGear? = null
    private var driving = false // D seen since the last arrival
    private var awaitingArrivalFix = false // parked, waiting for onCurrentLocationInfo to record the arrival
    private lateinit var statusView: TextView
    private val status = LinkedHashMap<String, String>() // latest value per key, shown top right
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private val mainHandler = Handler(Looper.getMainLooper())

    // System clock + timezone (car sets them automatically; emulator defaults to America/Los_Angeles).
    private val clock = object : Runnable {
        override fun run() {
            setStatus("Now", ZonedDateTime.now().withNano(0).toString())
            mainHandler.postDelayed(this, 1_000)
        }
    }

    private val ignitionCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            runOnUiThread {
                val state = IGNITION_STATES.getOrNull(value.value as? Int ?: -1) ?: value.value.toString()
                setStatus("Ignition (AAOS)", state)
                log("Ignition (AAOS) -> $state")
            }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            runOnUiThread { log("Ignition (AAOS) error event area=$areaId") }
        }
    }

    private var connected = false
    private var bookmarks: List<BookmarkInfo>? = null // null until the navigation answers
    private var fakeBookmarks = emptyList<BookmarkInfo>() // test only, kept apart so navigation updates don't erase them
    private var pendingParam: JSONObject? = null // Gleo action waiting for bookmarks
    private var destination: RouteInfo? = null
    private var routePending = false // destination chosen before NaviHelper connected
    private var inFlight = false // route requested, Gleo waiting for its callback

    // Emulator navigation emits onRouteStarted 5.8~7.7s after requestRoute, past Gleo's 7s limit,
    // so a request still unanswered at RESPONSE_TIMEOUT_MS is reported as accepted.
    private val timeout = Runnable {
        if (routePending) finishGleoAction("fail", "Could not connect to the navigation service.")
        else finishGleoAction("success", "The route to the destination has been requested.")
    }

    private val bookmarkWait = Runnable {
        log("Bookmarks not received, using the frequent place only")
        processPendingRequest()
    }

    private val naviListener = object : NaviHelperEventListener {
        override fun onBookmarkInfo(bookmarkInfo: List<BookmarkInfo>) {
            runOnUiThread {
                bookmarks = bookmarkInfo
                setStatus("Bookmarks", bookmarkInfo.joinToString { "${it.name}(${it.category})" })
                mainHandler.removeCallbacks(bookmarkWait)
                processPendingRequest()
            }
        }

        override fun onBookMarkChanged() {
            runOnUiThread { naviHelper.getBookmarkInfo() }
        }

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            runOnUiThread {
                log("onRouteStarted $routeStartInfo")
                finishGleoAction("success", "Route guidance to the destination has started.")
            }
        }

        override fun onRoutingCompleted() {
            runOnUiThread { log("onRoutingCompleted") }
        }

        override fun onDestinationChanged() {
            runOnUiThread { log("onDestinationChanged") }
        }

        override fun onError(errorCode: NaviErrorCode) {
            runOnUiThread {
                log("onError $errorCode")
                finishGleoAction("fail", "The navigation failed to find a route: $errorCode.")
            }
        }

        // Docs only say "ETA in seconds": travel seconds (~1e3) or epoch seconds (~1.8e9)? Compare with onRouteStarted duration.
        override fun onTripInfo(tripInfo: TripInfo) {
            runOnUiThread {
                setStatus("Trip to ${TRIP_TEST_POI.poiName}", "eta=${tripInfo.eta} distance=${tripInfo.distance}m")
                log("onTripInfo $tripInfo")
            }
        }

        // Answer to getCurrentLocationInfo() sent when the car parks after driving. Coordinates stay out of the log.
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
            runOnUiThread {
                if (!awaitingArrivalFix) return@runOnUiThread
                awaitingArrivalFix = false
                // ponytail: PARKED with coordinates only; NAVI arrivals with Kakao id/category need /search first
                val arrival = Arrival(null, null, null, info.latitude, info.longitude, VisitSource.PARKED)
                lifecycleScope.launch {
                    val visitId = appContainer.placeRepository.recordArrival(arrival)
                    // Kept in prefs so the departure is still recorded if the app restarts while parked.
                    getSharedPreferences(PREFS_EVENTS, Context.MODE_PRIVATE).edit().putLong(KEY_OPEN_VISIT, visitId).apply()
                    log("Arrival recorded as visit #$visitId")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusView = TextView(this).apply {
            setPadding(32, 32, 32, 32)
            textSize = 20f // sp
        }
        logView = TextView(this).apply {
            setPadding(32, 32, 32, 32)
            textSize = 16f // sp
        }
        logScroll = ScrollView(this).apply {
            addView(logView)
            setBackgroundColor(0xFFF2F2F2.toInt()) // separates the log from the status table above
        }
        // Left: change vehicle values through Virtual Console.
        // Right top: latest value per item as the SDK reads it back. Right bottom: event log.
        fun half() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        fun halfHeight() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        val testPage = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(vehicleSimPanel(this@MainActivity) { result -> runOnUiThread { log(result) } }, half())
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(ScrollView(this@MainActivity).apply { addView(statusView) }, halfHeight())
                addView(logScroll, halfHeight())
            }, half())
        }

        vehicle = Vehicle(applicationContext)
        vehicle.initialize()

        val placesPage = PlacesPage(this) {
            // The navigation SDK can't write bookmarks, so test favorites live only in this app until restart.
            fakeBookmarks = TEST_BOOKMARKS
            setStatus("Fake bookmarks", fakeBookmarks.joinToString { it.name.orEmpty() })
            log("Fake bookmarks added")
            Toast.makeText(this, R.string.fake_bookmarks_added, Toast.LENGTH_SHORT).show()
        }
        // Tabs on top (swipe works too): sensor test, calendar, frequent places.
        val pages = listOf(testPage, CalendarPage(this).view, placesPage.view)
        val pager = ViewPager2(this).apply {
            offscreenPageLimit = pages.size - 1 // keep every page attached so each View is created only once
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount() = pages.size
                override fun getItemViewType(position: Int) = position
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                    object : RecyclerView.ViewHolder(pages[viewType].apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }) {}
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
            }
        }
        val tabs = TabLayout(this)
        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.customView = TextView(this).apply {
                text = getString(TAB_TITLES[position])
                textSize = 22f // sp, car screen
                gravity = Gravity.CENTER
            }
        }.attach()
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            // TabLayout forces 48dp unless given an exact height
            addView(tabs, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (72 * resources.displayMetrics.density).toInt()))
            addView(pager, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })

        naviHelper = NaviHelper(applicationContext)
        naviHelper.initialize(
            onConnected = {
                runOnUiThread {
                    connected = true
                    naviHelper.addListener(naviListener)
                    setStatus("NaviHelper", "connected")
                    naviHelper.getBookmarkInfo()
                    requestTrip(TRIP_TEST_POI)
                    if (routePending) {
                        routePending = false
                        requestRoute()
                    }
                }
            },
            onFailed = { e ->
                runOnUiThread {
                    setStatus("NaviHelper", "failed")
                    log("NaviHelper connection failed: $e")
                    finishGleoAction("fail", "Could not connect to the navigation service.")
                }
            }
        )

        clock.run()
        // "Ignition" proxies: app start and the last BOOT_COMPLETED (not sent when the head unit resumes from suspend).
        setStatus("App started", ZonedDateTime.now().withNano(0).toString())
        setStatus(
            "Last boot (BOOT_COMPLETED)",
            getSharedPreferences(PREFS_EVENTS, Context.MODE_PRIVATE).getString(KEY_LAST_BOOT, null) ?: "-"
        )

        readVehicleStatus()
        watchCabin()
        watchPower()

        // Skip on recreation so the same Gleo action isn't replayed.
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // getReferrer() reads getIntent(), so a referrer extra on an earlier intent must not vouch for this one
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(timeout)
        mainHandler.removeCallbacks(bookmarkWait)
        mainHandler.removeCallbacks(clock)
        if (connected) naviHelper.removeListener(naviListener)
        naviHelper.release()
        vehicleUnsubscribers.forEach { it() }
        vehicle.release()
        car?.let {
            runCatching { (it.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager).unregisterCallback(ignitionCallback) }
            it.disconnect()
        }
        super.onDestroy()
    }

    // Start-of-trip signals: Pleos gear (official SDK) and AAOS IGNITION_STATE (Pleos SDK has no ignition API yet).
    private fun watchPower() {
        val gear = vehicle.getGear()
        val onGear = { selected: VehicleGear? ->
            runOnUiThread {
                setStatus("Gear", selected.toString())
                if (lastGear == VehicleGear.GEAR_PARK && selected != VehicleGear.GEAR_PARK) log("Departure: $lastGear -> $selected")
                if (lastGear != null && lastGear != selected) log("Gear $lastGear -> $selected")
                // Visit log: D starts a drive, P after a drive is an arrival. P -> R -> P while parking stays one visit.
                if (selected == VehicleGear.GEAR_DRIVE && !driving) {
                    driving = true
                    awaitingArrivalFix = false
                    recordDeparture()
                } else if (selected == VehicleGear.GEAR_PARK && driving) {
                    driving = false
                    awaitingArrivalFix = true
                    if (connected) naviHelper.getCurrentLocationInfo() else log("Arrival not recorded: NaviHelper not connected")
                }
                lastGear = selected
            }
        }
        gear.getCurrentGearSelectionState(onGear, vehicleError("Gear"))
        val gearListener = object : GearSelectionListener {
            override fun onSelected(selected: VehicleGear?) = onGear(selected)
            override fun onFailed(e: Exception) = vehicleError("Gear")(e)
        }
        gear.registerGearSelection(gearListener)
        vehicleUnsubscribers += { gear.unregisterGearSelection(gearListener) }

        runCatching {
            val connectedCar = Car.createCar(this).also { car = it }
            val properties = connectedCar.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            val subscribed = properties.registerCallback(
                ignitionCallback, VehiclePropertyIds.IGNITION_STATE, CarPropertyManager.SENSOR_RATE_ONCHANGE
            )
            if (!subscribed) setStatus("Ignition (AAOS)", "not supported")
        }.onFailure {
            setStatus("Ignition (AAOS)", "error")
            log("Ignition (AAOS) subscribe failed: $it")
        }
    }

    private fun recordDeparture() {
        val prefs = getSharedPreferences(PREFS_EVENTS, Context.MODE_PRIVATE)
        val visitId = prefs.getLong(KEY_OPEN_VISIT, 0L).takeIf { it > 0 } ?: return
        prefs.edit().remove(KEY_OPEN_VISIT).apply()
        lifecycleScope.launch {
            appContainer.placeRepository.recordDeparture(visitId)
            log("Departure recorded for visit #$visitId")
        }
    }

    // Reads and subscribes every seat/door the car reports, so Virtual Console changes show up live in the log.
    private fun watchCabin() {
        val rangeListener = object : RangeRemainingListener {
            override fun onRangeRemainingChanged(meters: Float?) = vehicleValue("Range remaining (m)")(meters)
            override fun onFailed(e: Exception) = vehicleError("Range remaining")(e)
        }
        vehicle.getOdometer().registerRangeRemainingListener(rangeListener)
        vehicleUnsubscribers += { vehicle.getOdometer().unregisterRangeRemainingListener(rangeListener) }

        val socListener = object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(percent: Int?) = vehicleValue("Battery SOC (%)")(percent)
            override fun onFailed(e: Exception) = vehicleError("Battery SOC")(e)
        }
        vehicle.getEvBattery().registerEvBatteryStateOfCharge(socListener)
        vehicleUnsubscribers += { vehicle.getEvBattery().unregisterEvBatteryStateOfCharge(socListener) }

        val seat = vehicle.getSeat()
        seat.checkSeatOccupancyCapabilities({ areas: List<SeatArea> ->
            runOnUiThread {
                areas.forEach { area ->
                    val label = "Seat ${area.values.joinToString()} occupied"
                    // SDK 2.3.2 returns the inverse on the emulator: raw SEAT_OCCUPANCY 1 is AAOS VACANT
                    // (Pleos seat belt warning service also treats it as empty) but the SDK reports true.
                    // Verified 2026-10-31 via Virtual Console Empty/Occupied. Remove the not() once the SDK is fixed
                    // or if a real car proves otherwise.
                    val report = { raw: Boolean? -> vehicleValue(label)(raw?.not()) }
                    seat.getCurrentSeatOccupancy(area, report, vehicleError(label))
                    val listener = object : SeatOccupancyListener {
                        override fun onSeatOccupancyUpdated(occupied: Boolean?) = report(occupied)
                        override fun onFailed(e: Exception) = vehicleError(label)(e)
                    }
                    seat.registerSeatOccupancy(area, listener)
                    vehicleUnsubscribers += { seat.unregisterSeatOccupancy(area, listener) }
                }
            }
        }, vehicleError("Seat occupancy capabilities"))

        seat.checkSeatBeltBuckledCapabilities({ areas: List<SeatArea> ->
            runOnUiThread {
                areas.forEach { area ->
                    val label = "Seat ${area.values.joinToString()} belt buckled"
                    seat.getCurrentSeatBeltBuckledState(area, vehicleValue(label), vehicleError(label))
                    val listener = object : SeatBeltBuckledStateListener {
                        override fun onSeatBeltBuckledStateUpdated(buckled: Boolean?) = vehicleValue(label)(buckled)
                        override fun onFailed(e: Exception) = vehicleError(label)(e)
                    }
                    seat.registerSeatBeltBuckledState(area, listener)
                    vehicleUnsubscribers += { seat.unregisterSeatBeltBuckledState(area, listener) }
                }
            }
        }, vehicleError("Seat belt capabilities"))

        // DoorValue covers side doors plus HOOD (frunk) and REAR (trunk).
        val door = vehicle.getDoor()
        door.checkDoorPositionCapabilities({ bounds: Map<DoorArea, AreaBounds<Int>> ->
            runOnUiThread {
                bounds.forEach { (area, range) ->
                    val label = "Door ${area.values.joinToString()} position"
                    log("$label bounds $range")
                    door.getCurrentDoorPosition(area, vehicleValue(label), vehicleError(label))
                    val listener = object : DoorPositionListener {
                        override fun onPositionUpdated(position: Int?) = vehicleValue(label)(position)
                        override fun onFailed(e: Exception) = vehicleError(label)(e)
                    }
                    door.registerDoorPosition(area, listener)
                    vehicleUnsubscribers += { door.unregisterDoorPosition(area, listener) }
                }
            }
        }, vehicleError("Door capabilities"))
    }

    // Hi-Pass card balance is not exposed: the Vehicle SDK Toll domain only has ETCS voice guidance volume.
    // Vehicle keeps private backing vals named like the getters, so call getX() explicitly.
    private fun readVehicleStatus() {
        vehicle.getEvBattery().getEvBatteryStateOfCharge(vehicleValue("Battery SOC (%)"), vehicleError("Battery SOC"))
        vehicle.getEvBattery().getEvBatteryLevel(vehicleValue("Battery energy (Wh)"), vehicleError("Battery energy"))
        vehicle.getFuel().getFuelPercent(vehicleValue("Fuel (%)"), vehicleError("Fuel"))
        vehicle.getOdometer().getRangeRemainingDistance(vehicleValue("Range remaining (m)"), vehicleError("Range remaining"))
    }

    private fun vehicleValue(label: String) = { value: Any? -> runOnUiThread { setStatus(label, value.toString()) } }

    private fun vehicleError(label: String) = { e: Exception ->
        runOnUiThread {
            setStatus(label, "error")
            log("$label failed: $e")
        }
    }

    private fun setStatus(key: String, value: String) {
        status[key] = value
        statusView.text = status.entries.joinToString("\n") { "${it.key}: ${it.value}" }
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action != ACTION_NAVIGATE && intent.action != "$packageName.$ACTION_NAVIGATE") return
        val hasReferrerExtra = intent.hasExtra(Intent.EXTRA_REFERRER) || intent.hasExtra(Intent.EXTRA_REFERRER_NAME)
        if (!isGleoCaller(referrer?.host, hasReferrerExtra, BuildConfig.DEBUG)) {
            log("Gleo action rejected: caller ${referrer?.host}")
            return
        }
        val param = runCatching { parseActionParam(intent.getStringExtra("ACTION_PARAM")) }
            .getOrElse {
                log("ACTION_PARAM parse failed: $it")
                JSONObject()
            }
        log("Gleo action ${intent.action} $param")

        pendingParam = param
        if (bookmarks != null) processPendingRequest()
        else mainHandler.postDelayed(bookmarkWait, BOOKMARK_WAIT_MS) // Gleo's 7s budget leaves room for a short wait
    }

    private fun processPendingRequest() {
        val param = pendingParam ?: return
        pendingParam = null

        lifecycleScope.launch {
            val candidates = findCandidates(param.optString("placeName"))
            val selected = param.optString("selectedPlace")
            val target = when {
                selected.isNotEmpty() -> candidates.firstOrNull {
                    it.route.poiName.contains(selected) || selected.contains(it.route.poiName)
                }
                candidates.size == 1 && param.optBoolean("confirmed") -> candidates.single()
                else -> null
            }

            when {
                target != null -> startRoute(target.route)
                candidates.isEmpty() -> sendGleoCallback(
                    "fail",
                    "No frequently visited place or favorite matches this name.",
                    JSONArray()
                )
                candidates.size > 1 -> sendGleoCallback(
                    "asking",
                    "Several places match. The user must choose which place to go to.",
                    candidatesData(candidates)
                )
                else -> sendGleoCallback(
                    "asking",
                    "Confirmation is required before starting route guidance to the destination.",
                    candidatesData(candidates)
                )
            }
        }
    }

    // Frequent places come from the on-device visit log (PlaceRepository), favorites from the navigation.
    private suspend fun findCandidates(placeName: String): List<Candidate> {
        if (placeName.isBlank()) return emptyList()
        val frequent = when (val match = appContainer.placeRepository.resolvePersonal(placeName)) {
            is PersonalMatch.Found -> listOf(match.place)
            is PersonalMatch.Ambiguous -> match.candidates
            PersonalMatch.NotFound -> emptyList() // ponytail: contract says fall back to /search, not wired yet
        }.map {
            Candidate(
                "frequently visited",
                RouteInfo(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    poiId = it.placeId ?: "",
                    poiName = it.displayName,
                    poiSubId = "",
                    address = ""
                )
            )
        }
        val favorites = (bookmarks.orEmpty() + fakeBookmarks)
            .filter { (it.name ?: "").contains(placeName) }
            .map {
                Candidate(
                    "favorite",
                    RouteInfo(
                        latitude = it.latitude,
                        longitude = it.longitude,
                        poiId = it.poiId ?: "",
                        poiName = it.name ?: "",
                        poiSubId = it.poiSubId ?: "",
                        address = it.address ?: "",
                        poiProvider = it.poiProvider ?: ""
                    )
                )
            }
        return frequent + favorites
    }

    private fun startRoute(route: RouteInfo) {
        destination = route
        inFlight = true
        mainHandler.removeCallbacks(timeout)
        mainHandler.postDelayed(timeout, RESPONSE_TIMEOUT_MS)
        if (connected) requestRoute() else routePending = true
    }

    private fun requestRoute() {
        val route = destination ?: return
        log("requestRoute -> ${route.poiName}")
        runCatching { naviHelper.requestRoute(route) }.onFailure {
            log("requestRoute failed: $it")
            finishGleoAction("fail", "The route request was rejected by the navigation service.")
        }
    }

    // Travel estimate only: needs NAVI_ROUTE, not NAVI_ROUTE_SEARCH, so the active route is left alone.
    private fun requestTrip(route: RouteInfo) {
        log("requestTrip -> ${route.poiName}")
        runCatching {
            naviHelper.requestTrip(TripRequest(route.latitude, route.longitude, route.poiId, route.poiName, route.poiSubId))
        }.onFailure { log("requestTrip failed: $it") }
    }

    // Answers the pending route request once; events not triggered by Gleo are only logged.
    private fun finishGleoAction(status: String, message: String) {
        if (!inFlight) return
        inFlight = false
        routePending = false
        mainHandler.removeCallbacks(timeout)
        val data = JSONArray().put(JSONObject().put("poiName", destination?.poiName))
        sendGleoCallback(status, message, data)
    }

    // Gleo reads data fields aloud; name and source only keep the speech short.
    private fun candidatesData(candidates: List<Candidate>): JSONArray =
        JSONArray(candidates.map { JSONObject().put("poiName", it.route.poiName).put("type", it.source) })

    private fun sendGleoCallback(status: String, message: String, data: JSONArray) {
        val response = JSONObject()
            .put("functionName", ACTION_NAVIGATE)
            .put("status", status)
            .put("message", message)
            .put("data", data.toString())
        // The response carries place names, so only Gleo receives it
        sendBroadcast(
            Intent(ACTION_GLEO_RESPONSE)
                .setPackage(GLEO_PACKAGE)
                .putExtra("packageName", packageName)
                .putExtra("response", response.toString())
        )
        log("Gleo callback $response")
    }

    // Gleo doc samples send ACTION_PARAM both as a JSON object and as a quoted JSON string.
    private fun parseActionParam(raw: String?): JSONObject {
        val value = JSONTokener(raw ?: "{}").nextValue()
        return value as? JSONObject ?: JSONObject(value as String)
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        logView.append("${ZonedDateTime.now().toLocalTime().withNano(0)} $message\n")
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private companion object {
        const val TAG = "GleoNaviTest"
        const val ACTION_NAVIGATE = "NAVIGATE_TO_PLACE"
        const val ACTION_GLEO_RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
        const val RESPONSE_TIMEOUT_MS = 5_000L // Gleo cancels the action after 7s
        const val BOOKMARK_WAIT_MS = 1_500L
        val TAB_TITLES = listOf(R.string.tab_sensor, R.string.calendar_title, R.string.places_title)
        const val KEY_OPEN_VISIT = "open_visit_id" // in PREFS_EVENTS: visit still waiting for its departure
        val IGNITION_STATES = listOf("UNDEFINED", "LOCK", "OFF", "ACC", "ON", "START") // AAOS VehicleIgnitionState

        // Made-up favorites near the demo visits (Pangyo). "유치원" -> 해님유치원(visit log) + 새싹유치원 = choose one;
        // "헬스장" -> favorite only = confirm. Names don't contain each other, so selectedPlace matching stays unambiguous.
        val TEST_BOOKMARKS = listOf(
            testBookmark("새싹유치원", 37.39700, 127.11700),
            testBookmark("판교 헬스장", 37.39400, 127.10700),
        )

        fun testBookmark(name: String, latitude: Double, longitude: Double) = BookmarkInfo(
            latitude = latitude,
            longitude = longitude,
            displayLatitude = null,
            displayLongitude = null,
            name = name,
            poiId = "",
            poiSubId = "",
            address = "",
            category = BookmarkType.DEFAULT,
            poiProvider = ""
        )

        // requestTrip test target only (Gleo candidates come from PlaceRepository): POI from the official NaviHelper docs.
        val TRIP_TEST_POI = RouteInfo(
            latitude = 37.48544722,
            longitude = 127.03636666,
            poiId = "36khp37hh8gqcm5nqhe5",
            poiName = "42dot",
            poiSubId = "0",
            address = "서울시 강남구 남부순환로 2621"
        )
    }
}
