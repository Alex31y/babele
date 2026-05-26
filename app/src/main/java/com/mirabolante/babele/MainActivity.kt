package com.mirabolante.babele

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.INTERNET
import android.Manifest.permission.MODIFY_AUDIO_SETTINGS
import android.Manifest.permission.RECORD_AUDIO
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mirabolante.babele.ui.BabeleScaffold
import com.mirabolante.babele.ui.theme.BabeleTheme

class MainActivity : ComponentActivity() {
  companion object {
    val PERMISSIONS: Array<String> =
        arrayOf(BLUETOOTH, BLUETOOTH_CONNECT, INTERNET, RECORD_AUDIO, MODIFY_AUDIO_SETTINGS)
  }

  private var permissionsGrantedState = mutableStateOf(false)

  private val permissionCheckLauncher =
      registerForActivityResult(RequestMultiplePermissions()) { result ->
        permissionsGrantedState.value = result.values.all { it } || hasAllPermissions()
      }

  private fun hasAllPermissions(): Boolean {
    return PERMISSIONS.all {
      checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    permissionsGrantedState.value = hasAllPermissions()
    setContent {
      BabeleTheme {
        val granted by permissionsGrantedState
        BabeleScaffold(permissionsGranted = granted)
      }
    }
  }

  override fun onStart() {
    super.onStart()
    if (!hasAllPermissions()) {
      permissionCheckLauncher.launch(PERMISSIONS)
    } else {
      permissionsGrantedState.value = true
    }
  }
}
