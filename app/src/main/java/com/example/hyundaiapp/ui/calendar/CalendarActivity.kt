package com.example.hyundaiapp.ui.calendar

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.hyundaiapp.R
import com.example.hyundaiapp.data.local.CalendarEventEntity
import com.example.hyundaiapp.ui.pairing.PairingActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 캐시한 일정을 날짜별로 보여 준다. 화면에 들어올 때마다 서버에서 새로 받아 온다 */
class CalendarActivity : AppCompatActivity() {

    private val viewModel: CalendarViewModel by viewModels { CalendarViewModel.Factory }

    private lateinit var lastSyncText: TextView
    private lateinit var noticeText: TextView
    private lateinit var linkButton: Button
    private lateinit var refreshButton: Button
    private lateinit var unlinkButton: Button
    private lateinit var eventList: LinearLayout

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dateFormat = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
    private val shortDateFormat = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)

        lastSyncText = findViewById(R.id.lastSyncText)
        noticeText = findViewById(R.id.noticeText)
        linkButton = findViewById(R.id.linkButton)
        refreshButton = findViewById(R.id.refreshButton)
        unlinkButton = findViewById(R.id.unlinkButton)
        eventList = findViewById(R.id.eventList)

        findViewById<View>(R.id.fakeModeText).visibility = if (viewModel.isFakeServer) View.VISIBLE else View.GONE
        linkButton.setOnClickListener { startActivity(Intent(this, PairingActivity::class.java)) }
        refreshButton.setOnClickListener { viewModel.refresh() }
        unlinkButton.setOnClickListener { viewModel.unlink() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.state, viewModel.events, ::Pair).collect { (state, events) -> render(state, events) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 앱 시작, QR 연동 후 돌아왔을 때 모두 여기서 연동 여부를 확인하고 갱신한다
        viewModel.start()
    }

    private fun render(state: CalendarUiState, events: List<CalendarEventEntity>) {
        lastSyncText.text = state.lastSyncMillis
            ?.let { getString(R.string.calendar_last_sync, timeFormat.format(Instant.ofEpochMilli(it).atZone(zone))) }
            ?: getString(R.string.calendar_never_synced)
        refreshButton.isEnabled = !state.syncing && state.retryAfterSec == null
        unlinkButton.visibility = if (state.link == LinkState.LINKED) View.VISIBLE else View.GONE
        linkButton.visibility = if (state.link == LinkState.NEEDS_LINK) View.VISIBLE else View.GONE

        val notice = when {
            state.link == LinkState.NEEDS_LINK -> getString(R.string.calendar_needs_link)
            state.notice == Notice.OFFLINE -> getString(R.string.calendar_offline)
            state.notice == Notice.SERVER_ERROR -> getString(R.string.calendar_server_error)
            state.retryAfterSec != null -> getString(R.string.calendar_rate_limited, state.retryAfterSec)
            state.notice == Notice.UNLINK_FAILED -> getString(R.string.calendar_unlink_failed)
            state.link == LinkState.UNKNOWN && events.isEmpty() -> getString(R.string.calendar_checking)
            state.link == LinkState.LINKED && events.isEmpty() -> getString(R.string.calendar_empty)
            else -> null
        }
        noticeText.text = notice
        noticeText.visibility = if (notice == null) View.GONE else View.VISIBLE

        renderEvents(events)
    }

    private fun renderEvents(events: List<CalendarEventEntity>) {
        eventList.removeAllViews()
        val inflater = layoutInflater
        events.groupBy { it.startDate() }.forEach { (date, dayEvents) ->
            val header = inflater.inflate(R.layout.item_event_date, eventList, false) as TextView
            header.text = dateFormat.format(date)
            eventList.addView(header)
            dayEvents.forEach { event ->
                val row = inflater.inflate(R.layout.item_event, eventList, false)
                row.findViewById<TextView>(R.id.timeText).text = event.timeLabel()
                row.findViewById<TextView>(R.id.titleText).text = event.title ?: getString(R.string.calendar_no_title)
                row.findViewById<TextView>(R.id.locationText).apply {
                    text = event.location
                    visibility = if (event.location == null) View.GONE else View.VISIBLE
                }
                eventList.addView(row)
            }
        }
    }

    private fun CalendarEventEntity.startDate(): LocalDate = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()

    private fun CalendarEventEntity.timeLabel(): String {
        if (allDay) {
            // 종일 일정의 끝은 포함하지 않는 날짜라 하루를 빼서 보여 준다
            val lastDay = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate().minusDays(1)
            return if (lastDay.isAfter(startDate())) {
                getString(R.string.calendar_all_day_range, shortDateFormat.format(lastDay))
            } else {
                getString(R.string.calendar_all_day)
            }
        }
        val start = Instant.ofEpochMilli(startMillis).atZone(zone)
        val end = Instant.ofEpochMilli(endMillis).atZone(zone)
        return "${timeFormat.format(start)} ~ ${timeFormat.format(end)}"
    }
}
