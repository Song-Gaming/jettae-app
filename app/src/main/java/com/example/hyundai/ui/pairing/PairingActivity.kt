package com.example.hyundai.ui.pairing

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.hyundai.R
import kotlinx.coroutines.launch

/** 폰으로 찍을 QR을 보여 주고, 연동이 끝나면 닫힌다. 결과는 일정 화면이 onStart에서 새로 읽는다 */
class PairingActivity : AppCompatActivity() {

    private val viewModel: PairingViewModel by viewModels { PairingViewModel.Factory }

    private lateinit var qrImage: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var retryButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing)

        qrImage = findViewById(R.id.qrImage)
        progress = findViewById(R.id.progress)
        statusText = findViewById(R.id.statusText)
        retryButton = findViewById(R.id.retryButton)
        retryButton.setOnClickListener { viewModel.retry() }
        findViewById<Button>(R.id.closeButton).setOnClickListener { finish() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setVisible(true)
    }

    override fun onStop() {
        viewModel.setVisible(false)
        super.onStop()
    }

    private fun render(state: PairingUiState) {
        progress.visibility = if (state is PairingUiState.Loading) View.VISIBLE else View.GONE
        qrImage.visibility = if (state is PairingUiState.ShowQr) View.VISIBLE else View.INVISIBLE
        retryButton.visibility = if (state is PairingUiState.Failed) View.VISIBLE else View.GONE
        when (state) {
            PairingUiState.Loading -> statusText.setText(R.string.pairing_loading)
            is PairingUiState.ShowQr -> {
                qrImage.setImageBitmap(state.qr)
                statusText.text = getString(R.string.pairing_remaining, state.remainingSec / 60, state.remainingSec % 60)
            }
            PairingUiState.Linked -> {
                statusText.setText(R.string.pairing_linked)
                finish()
            }
            is PairingUiState.Failed -> statusText.setText(
                when (state.reason) {
                    FailReason.LOGIN_FAILED -> R.string.pairing_login_failed
                    FailReason.EXPIRED -> R.string.pairing_expired
                    FailReason.NETWORK -> R.string.pairing_network_error
                }
            )
        }
    }
}
