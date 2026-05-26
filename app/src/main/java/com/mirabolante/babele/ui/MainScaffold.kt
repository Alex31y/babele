package com.mirabolante.babele.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mirabolante.babele.translation.TranslationViewModel

@Composable
fun BabeleScaffold(
    permissionsGranted: Boolean,
    modifier: Modifier = Modifier,
) {
  var inTranslation by rememberSaveable { mutableStateOf(false) }

  Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(modifier = Modifier.fillMaxSize()) {
      if (!inTranslation || !permissionsGranted) {
        HomeScreen(
            onContinue = { if (permissionsGranted) inTranslation = true },
        )
      } else {
        val translationVm: TranslationViewModel = viewModel()
        TranslationScreen(
            viewModel = translationVm,
            onBack = {
              translationVm.stop()
              inTranslation = false
            },
        )
      }
    }
  }
}
