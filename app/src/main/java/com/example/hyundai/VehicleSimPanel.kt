package com.example.hyundai

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * 에뮬레이터 전용 테스트 패널: Pleos Connect Virtual Console의 내부 HTTP API(에뮬레이터 안 127.0.0.1:8088)로
 * 차량 값(VHAL)을 바꾼다. 앱은 바뀐 값을 Vehicle SDK 리스너로 다시 읽는다.
 * 비공식 API(콘솔 웹 페이지가 쓰는 /vehicle/catalog의 write_endpoint)라 에뮬레이터에서만 동작한다.
 */
fun vehicleSimPanel(context: Context, onResult: (String) -> Unit): View {
    val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(32, 32, 32, 32)
    }
    val put = { query: String -> consoleRequest("PUT", query, onResult) }

    fun label(text: String) = TextView(context).apply {
        this.text = text
        textSize = 20f // sp
    }

    fun slider(name: String, max: Int, unit: String, query: (Int) -> String) {
        val title = label("$name: - $unit")
        val bar = SeekBar(context).apply {
            this.max = max
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    title.text = "$name: $value $unit"
                }

                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = put(query(bar.progress))
            })
        }
        column.addView(title)
        column.addView(bar)
    }

    fun toggle(name: String, query: (Boolean) -> String) {
        column.addView(SwitchCompat(context).apply {
            text = name
            textSize = 20f // sp
            setOnCheckedChangeListener { _, checked -> put(query(checked)) }
        })
    }

    fun buttons(name: String, options: List<String>, onClick: (String) -> Unit) {
        column.addView(label(name))
        column.addView(LinearLayout(context).apply {
            options.forEach { option ->
                addView(Button(context).apply {
                    text = option
                    setOnClickListener { onClick(option) }
                })
            }
        })
    }

    column.addView(label("차량 조작 (Virtual Console)"))
    slider("남은 주행거리", 1000, "km") { "/vehicle/odometer?remaining_range_m=${it * 1000}" }
    slider("배터리 SOC", 100, "%") { "/vehicle/ev-battery?field=soc&value=$it" }
    listOf("driver" to "운전석", "passenger" to "조수석", "rear_left" to "뒷좌석 왼쪽", "rear_right" to "뒷좌석 오른쪽")
        .forEach { (area, name) -> toggle("$name 탑승") { "/vehicle/seat?kind=occupied&value=$it&area=$area" } }
    listOf(
        "door_fl" to "앞 왼쪽 문", "door_fr" to "앞 오른쪽 문", "door_rl" to "뒤 왼쪽 문", "door_rr" to "뒤 오른쪽 문",
        "trunk" to "트렁크", "frunk" to "프렁크",
    ).forEach { (field, name) -> toggle("$name 열림") { "/vehicle/body?$field=${if (it) 1 else 0}" } }

    // Outside P the system covers this (non distraction-optimized) app, so its own P button can't be pressed;
    // D/R/N return to P automatically.
    buttons("기어 (P 외에는 ${GEAR_RETURN_MS / 1000}초 뒤 자동으로 P)", listOf("Park", "Reverse", "Neutral", "Drive")) { gear ->
        put("/vehicle/gear?gear=$gear")
        if (gear != "Park") column.postDelayed({ put("/vehicle/gear?gear=Park") }, GEAR_RETURN_MS)
    }
    // Hidden card in Virtual Console (visible:false). Off/StandBy left out: they may suspend the emulator.
    buttons("전원 상태 (숨김 기능, 실험)", listOf("Ready", "On", "Driving")) { state ->
        put("/vehicle/power-system/simulator?area_id=0&state=$state")
    }

    return ScrollView(context).apply {
        addView(column)
        // The console only bridges writes to the emulator while a client heartbeats /browser/session
        // every second (its web page does this); without it every PUT returns 503.
        val heartbeat = object : Runnable {
            override fun run() {
                consoleRequest("POST", "/browser/session?client=$CONSOLE_CLIENT") {}
                postDelayed(this, 1_000)
            }
        }
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                view.post(heartbeat)
            }

            override fun onViewDetachedFromWindow(view: View) {
                view.removeCallbacks(heartbeat)
                consoleRequest("POST", "/browser/session?client=$CONSOLE_CLIENT&closing=1") {}
            }
        })
    }
}

private const val CONSOLE_CLIENT = "hyundai-test-app"
private const val GEAR_RETURN_MS = 5_000L

// Runs off the main thread; onResult is called from the worker thread.
private fun consoleRequest(method: String, query: String, onResult: (String) -> Unit) {
    thread {
        val result = runCatching {
            val connection = URL("http://127.0.0.1:8088$query").openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 2_000
            connection.readTimeout = 2_000
            try {
                "HTTP ${connection.responseCode}"
            } finally {
                connection.disconnect()
            }
        }.getOrElse { "failed: $it" }
        onResult("$method $query -> $result")
    }
}
