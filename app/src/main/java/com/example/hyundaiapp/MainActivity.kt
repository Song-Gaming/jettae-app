package com.example.hyundaiapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import com.example.hyundaiapp.ui.calendar.CalendarActivity

class MainActivity : AppCompatActivity() {

    // 화면이 살아 있는 동안 계속 쓰는 차량 서비스 연결
    private lateinit var vehicle: Vehicle

    private lateinit var resultText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultText = findViewById(R.id.resultText)
        findViewById<Button>(R.id.refreshButton).setOnClickListener { readDoorPosition() }
        findViewById<Button>(R.id.calendarButton).setOnClickListener {
            startActivity(Intent(this, CalendarActivity::class.java))
        }

        // 차량 서비스 연결. release()는 onDestroy에서 호출
        vehicle = Vehicle(this)
        vehicle.initialize()

        readDoorPosition()
    }

    // 운전석(1열 왼쪽) 도어 위치 조회. 결과는 콜백으로 비동기 전달됨
    private fun readDoorPosition() {
        resultText.text = getString(R.string.door_loading)
        vehicle.getDoor().getCurrentDoorPosition(
            area = DoorArea(DoorValue.ROW_1_LEFT),
            onComplete = { pos: Int? ->
                Log.d(TAG, "도어 위치 조회 성공: $pos")
                // 콜백이 메인 스레드가 아닐 수 있어 화면 갱신은 UI 스레드에서
                runOnUiThread { resultText.text = getString(R.string.door_value, pos.toString()) }
            },
            onFailed = { e: Exception ->
                Log.e(TAG, "도어 위치 조회 실패", e)
                runOnUiThread { resultText.text = getString(R.string.door_failed, e.message ?: e.javaClass.simpleName) }
            }
        )
    }

    override fun onDestroy() {
        vehicle.release()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "VehicleTest"
    }
}
