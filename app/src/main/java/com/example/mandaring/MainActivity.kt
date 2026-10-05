package com.example.mandaring

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.mandaring.ui.RecordScreen
import com.example.mandaring.ui.RecordViewModel
import com.example.mandaring.ui.theme.ToneTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: RecordViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) showDebugWav(intent)
        setContent {
            ToneTheme {
                RecordScreen(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showDebugWav(intent)
    }

    /**
     * Debug builds only: shows a WAV placed in the app's `files/debug` directory, so the analysis
     * can be checked on the device with known audio. See tools/show_sample.sh.
     */
    private fun showDebugWav(intent: Intent) {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val name = intent.getStringExtra(EXTRA_DEBUG_WAV) ?: return
        if (debuggable) viewModel.select(File(File(filesDir, "debug"), File(name).name))
    }

    private companion object {
        const val EXTRA_DEBUG_WAV = "debug_wav"
    }
}
