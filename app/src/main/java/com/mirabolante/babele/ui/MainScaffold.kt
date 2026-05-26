package com.mirabolante.babele.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mirabolante.babele.config.ApiKeyStore
import com.mirabolante.babele.translation.AudioMode
import com.mirabolante.babele.translation.TranslationViewModel

@Composable
fun BabeleScaffold(
    permissionsGranted: Boolean,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val apiKeyStore = remember { ApiKeyStore(context) }

  var hasKey by remember { mutableStateOf(apiKeyStore.hasUserKey()) }
  var showSettings by remember { mutableStateOf(false) }
  // null = on the home screen; non-null = the mode chosen to enter translation with.
  var chosenMode by remember { mutableStateOf<AudioMode?>(null) }

  Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(modifier = Modifier.fillMaxSize()) {
      when {
        // Forced key setup until the user saves their own key, or opened from settings.
        !hasKey || showSettings ->
            ApiKeyScreen(
                initialKey = apiKeyStore.prefillKey(),
                forced = !hasKey,
                onSave = { key ->
                  apiKeyStore.setKey(key)
                  hasKey = true
                  showSettings = false
                },
                onClear = {
                  apiKeyStore.clear()
                  hasKey = false
                },
                onClose = { showSettings = false },
            )
        chosenMode == null ->
            HomeScreen(
                onStart = { picked -> if (permissionsGranted) chosenMode = picked },
                onOpenSettings = { showSettings = true },
            )
        else -> {
          val mode = chosenMode!!
          val translationVm: TranslationViewModel = viewModel()
          LaunchedEffect(mode) { translationVm.setAudioMode(mode) }
          TranslationScreen(
              viewModel = translationVm,
              onBack = {
                translationVm.stop()
                chosenMode = null
              },
          )
        }
      }
    }
  }
}
