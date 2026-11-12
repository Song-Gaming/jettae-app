package com.example.hyundai.ui.places

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.hyundai.R
import com.example.hyundai.data.places.FrequentPlace
import com.example.hyundai.data.places.PersonalMatch
import com.example.hyundai.data.places.PlaceRole
import com.example.hyundai.data.places.RoleSource
import kotlinx.coroutines.launch

/**
 * 방문 기록으로 분류한 장소 목록. 장소를 누르면 종류를 직접 고칠 수 있다.
 * 위쪽 버튼은 /parse가 kind: personal로 돌려줄 말을 흉내 내 "유치원 → 어느 유치원"을 확인한다.
 * MainActivity 탭 페이지. ViewModel과 수명 주기는 탭을 가진 [activity]의 것을 쓴다.
 * [onFakeBookmarks]: 가짜 즐겨찾기 버튼. 즐겨찾기는 MainActivity가 들고 있어서 그쪽에 맡긴다.
 */
class PlacesPage(private val activity: AppCompatActivity, onFakeBookmarks: () -> Unit) {

    private val viewModel: PlacesViewModel by activity.viewModels { PlacesViewModel.Factory }

    val view: View = activity.layoutInflater.inflate(R.layout.activity_places, null)

    private val resultText: TextView = view.findViewById(R.id.resultText)
    private val suggestionText: TextView = view.findViewById(R.id.suggestionText)
    private val placeList: LinearLayout = view.findViewById(R.id.placeList)

    init {
        view.findViewById<Button>(R.id.seedButton).setOnClickListener { viewModel.seedDemo() }
        view.findViewById<Button>(R.id.clearButton).setOnClickListener { viewModel.clear() }
        view.findViewById<Button>(R.id.fakeBookmarksButton).setOnClickListener { onFakeBookmarks() }

        val queryButtons = view.findViewById<LinearLayout>(R.id.queryButtons)
        TRY_QUERIES.forEach { query ->
            val button = Button(activity).apply {
                text = query
                textSize = 20f
                minHeight = resources.getDimensionPixelSize(R.dimen.touch_target)
                setOnClickListener { viewModel.resolve(query) }
            }
            queryButtons.addView(button)
        }

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = viewModel.load()
        })
    }

    private val layoutInflater get() = activity.layoutInflater

    private fun getString(id: Int, vararg args: Any?): String = activity.getString(id, *args)

    private fun render(state: PlacesUiState) {
        resultText.text = when (val match = state.match) {
            is PersonalMatch.Found -> getString(R.string.places_found, state.lastQuery, match.place.displayName)
            is PersonalMatch.Ambiguous ->
                getString(R.string.places_ambiguous, state.lastQuery, match.candidates.joinToString { it.displayName })
            PersonalMatch.NotFound -> getString(R.string.places_not_found, state.lastQuery)
            null -> if (state.places.isEmpty()) getString(R.string.places_empty) else ""
        }
        suggestionText.text = state.suggestion
            ?.let { getString(R.string.places_suggestion, it.displayName) }
            ?: getString(R.string.places_no_suggestion)
        suggestionText.visibility = if (state.places.isEmpty()) View.GONE else View.VISIBLE

        placeList.removeAllViews()
        state.places.forEach { place -> placeList.addView(placeRow(place)) }
    }

    private fun placeRow(place: FrequentPlace): View {
        val row = layoutInflater.inflate(R.layout.item_place, placeList, false)
        row.findViewById<TextView>(R.id.roleText).text = place.role.label
        row.findViewById<TextView>(R.id.nameText).text = place.displayName
        row.findViewById<TextView>(R.id.metaText).text =
            getString(R.string.places_row_meta, sourceLabel(place.roleSource), place.visitsLast30Days, place.score)
        row.findViewById<View>(R.id.frequentText).visibility = if (place.isFrequent) View.VISIBLE else View.INVISIBLE
        row.setOnClickListener { showRoleChooser(place) }
        return row
    }

    private fun sourceLabel(source: RoleSource): String = getString(
        when (source) {
            RoleSource.USER -> R.string.places_source_user
            RoleSource.CATEGORY -> R.string.places_source_category
            RoleSource.PATTERN -> R.string.places_source_pattern
            RoleSource.NONE -> R.string.places_source_none
        }
    )

    private fun showRoleChooser(place: FrequentPlace) {
        val roles = PlaceRole.entries
        val items = (roles.map { it.label } + getString(R.string.places_reset_role)).toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle(getString(R.string.places_choose_role) + " · " + place.displayName)
            .setItems(items) { _, which -> viewModel.setRole(place.key, roles.getOrNull(which)) }
            .show()
    }

    private companion object {
        val TRY_QUERIES = listOf("유치원", "마트", "세탁소", "집", "회사")
    }
}
