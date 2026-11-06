package com.example.hyundaiapp.ui.places

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.hyundaiapp.R
import com.example.hyundaiapp.data.places.FrequentPlace
import com.example.hyundaiapp.data.places.PersonalMatch
import com.example.hyundaiapp.data.places.PlaceRole
import com.example.hyundaiapp.data.places.RoleSource
import kotlinx.coroutines.launch

/**
 * 방문 기록으로 분류한 장소 목록. 장소를 누르면 종류를 직접 고칠 수 있다.
 * 위쪽 버튼은 /parse가 kind: personal로 돌려줄 말을 흉내 내 "유치원 → 어느 유치원"을 확인한다.
 */
class PlacesActivity : AppCompatActivity() {

    private val viewModel: PlacesViewModel by viewModels { PlacesViewModel.Factory }

    private lateinit var resultText: TextView
    private lateinit var suggestionText: TextView
    private lateinit var placeList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_places)

        resultText = findViewById(R.id.resultText)
        suggestionText = findViewById(R.id.suggestionText)
        placeList = findViewById(R.id.placeList)
        findViewById<Button>(R.id.seedButton).setOnClickListener { viewModel.seedDemo() }
        findViewById<Button>(R.id.clearButton).setOnClickListener { viewModel.clear() }

        val queryButtons = findViewById<LinearLayout>(R.id.queryButtons)
        TRY_QUERIES.forEach { query ->
            val button = Button(this).apply {
                text = query
                textSize = 20f
                minHeight = resources.getDimensionPixelSize(R.dimen.touch_target)
                setOnClickListener { viewModel.resolve(query) }
            }
            queryButtons.addView(button)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.load()
    }

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
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.places_choose_role) + " · " + place.displayName)
            .setItems(items) { _, which -> viewModel.setRole(place.key, roles.getOrNull(which)) }
            .show()
    }

    private companion object {
        val TRY_QUERIES = listOf("유치원", "마트", "세탁소", "집", "회사")
    }
}
