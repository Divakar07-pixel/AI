package com.example.voiceassistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.voiceassistant.service.VoiceAssistantForegroundService
import com.example.voiceassistant.ui.MainViewModel
import com.example.voiceassistant.ui.VoiceAssistantScreen
import com.example.voiceassistant.ui.theme.VoiceAssistantTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var hasRecordAudioPermission by mutableStateOf(false)
    private var hasContactsPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        hasRecordAudioPermission = recordAudioGranted
        hasContactsPermission = permissions[Manifest.permission.READ_CONTACTS]
            ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPermissions()

        setContent {
            VoiceAssistantTheme(darkTheme = true, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VoiceAssistantScreen(
                        viewModel = viewModel,
                        hasRecordAudioPermission = hasRecordAudioPermission,
                        hasContactsPermission = hasContactsPermission,
                        onRequestPermission = { requestRequiredPermissions() },
                        onRequestContactsPermission = { requestContactsPermission() }
                    )
                }
            }
        }
        handleWakeWordIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWakeWordIntent(intent)
    }

    private fun handleWakeWordIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(VoiceAssistantForegroundService.EXTRA_WAKE_WORD_DETECTED, false) == true) {
            intent.removeExtra(VoiceAssistantForegroundService.EXTRA_WAKE_WORD_DETECTED)
            window.decorView.post {
                if (hasRecordAudioPermission) viewModel.startListening()
                else requestRequiredPermissions()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        hasRecordAudioPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        hasContactsPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestContactsPermission() {
        permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }
}
