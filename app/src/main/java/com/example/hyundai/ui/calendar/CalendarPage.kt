package com.example.hyundai.ui.calendar

import android.content.Intent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.hyundai.R
import com.example.hyundai.data.local.CalendarEventEntity
import com.example.hyundai.ui.pairing.PairingActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 캐시한 일정을 날짜별로 보여 주는 MainActivity 탭 페이지. 앱이 화면에 나올 때마다 서버에서 새로 받아 온다.
 * ViewModel과 수명 주기는 탭을 가진 [activity]의 것을 쓴다.
 */
class CalendarPage(private val activity: AppCompatActivity, onSuggestDeparture: () -> Unit) {

    private val viewModel: CalendarViewModel by activity.viewModels { CalendarViewModel.Factory }

    val view: View = activity.layoutInflater.inflate(R.layout.activity_calendar, null)

    private val lastSyncText: TextView = view.findViewById(R.id.lastSyncText)
    private val noticeText: TextView = view.findViewById(R.id.noticeText)
    private val linkButton: Button = view.findViewById(R.id.linkButton)
    private val refreshButton: Button = view.findViewById(R.id.refreshButton)
    private val unlinkButton: Button = view.findViewById(R.id.unlinkButton)
    private val eventList: LinearLayout = view.findViewById(R.id.eventList)

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dateFormat = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
    private val shortDateFormat = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    init {
        setUpServerMode()
        view.findViewById<Button>(R.id.suggestButton).setOnClickListener { onSuggestDeparture() }
        linkButton.setOnClickListener { activity.startActivity(Intent(activity, PairingActivity::class.java)) }
        refreshButton.setOnClickListener { viewModel.refresh() }
        unlinkButton.setOnClickListener { viewModel.unlink() }

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.state, viewModel.events, ::Pair).collect { (state, events) -> render(state, events) }
            }
        }
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            // 앱 시작, QR 연동 후 돌아왔을 때 모두 여기서 연동 여부를 확인하고 갱신한다.
            // QR 화면에서 가짜 일정을 골랐을 수 있으니 선택 목록도 맞춘다(같은 값이면 변경 이벤트는 무시됨)
            override fun onStart(owner: LifecycleOwner) {
                modeSpinner.setSelection(viewModel.serverMode.mode - modeOffset)
                viewModel.start()
            }
        })
    }

    private val modeSpinner: Spinner get() = view.findViewById(R.id.serverModeSpinner)
    private val modeOffset: Int get() = if (viewModel.serverMode.hasRealServer) 0 else 1

    /** [출발 제안 받기]를 누른 결과. 차량 화면에서는 토스트가 보이지 않아 버튼 옆에 적는다 */
    fun showSuggestResult(message: Int) {
        view.findViewById<TextView>(R.id.suggestResultText).setText(message)
    }

    /** 목록 위치 → 모드 번호. SERVER_URL 없이 빌드했으면 "실제 서버"를 뺀다 */
    private fun setUpServerMode() {
        val store = viewModel.serverMode
        val offset = modeOffset
        val labels = activity.resources.getStringArray(R.array.server_modes).drop(offset)
        modeSpinner.apply {
            adapter = ArrayAdapter(activity, R.layout.item_spinner, labels)
            setSelection(store.mode - offset)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    if (position + offset == store.mode) return
                    this@CalendarPage.view.findViewById<TextView>(R.id.suggestResultText).text = null
                    viewModel.changeServerMode(position + offset)
                }

                override fun onNothingSelected(parent: AdapterView<*>) = Unit
            }
        }
    }

    private val layoutInflater get() = activity.layoutInflater

    private fun getString(id: Int, vararg args: Any?): String = activity.getString(id, *args)

    private fun render(state: CalendarUiState, events: List<CalendarEventEntity>) {
        lastSyncText.text = state.lastSyncMillis
            ?.let { getString(R.string.calendar_last_sync, timeFormat.format(Instant.ofEpochMilli(it).atZone(zone))) }
            ?: getString(R.string.calendar_never_synced)
        refreshButton.isEnabled = !state.syncing && state.retryAfterSec == null
        unlinkButton.visibility = if (state.link == LinkState.LINKED) View.VISIBLE else View.GONE
        // 서버가 응답하지 않을 때도 보여 준다: QR 화면에서 서버 오류를 알리고 가짜 일정으로 넘어갈 수 있다
        val serverDown = state.notice == Notice.OFFLINE || state.notice == Notice.SERVER_ERROR
        linkButton.visibility =
            if (state.link == LinkState.NEEDS_LINK || (state.link != LinkState.LINKED && serverDown)) View.VISIBLE else View.GONE

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
