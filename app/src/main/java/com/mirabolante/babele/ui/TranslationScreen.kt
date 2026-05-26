package com.mirabolante.babele.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirabolante.babele.R
import com.mirabolante.babele.translation.LanguageOption
import com.mirabolante.babele.translation.TranslationStatus
import com.mirabolante.babele.translation.TranslationTurn
import com.mirabolante.babele.translation.TranslationViewModel

@Composable
fun TranslationScreen(
    viewModel: TranslationViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var pickerOpen by remember { mutableStateOf(false) }
  val listState = rememberLazyListState()

  LaunchedEffect(uiState.turns.size) {
    if (uiState.turns.isNotEmpty()) listState.animateScrollToItem(uiState.turns.lastIndex)
  }

  Column(
      modifier =
          modifier
              .fillMaxSize()
              .statusBarsPadding()
              .navigationBarsPadding()
              .padding(horizontal = 16.dp),
  ) {
    TopBar(onBack = onBack)

    TargetLanguageCard(
        language = uiState.targetLanguage,
        enabled = !uiState.isActive,
        onClick = { pickerOpen = true },
    )

    Spacer(modifier = Modifier.height(12.dp))

    AudioRouteToggle(
        useGlasses = uiState.useGlassesAudio,
        enabled = !uiState.isActive,
        onSelect = { viewModel.setUseGlassesAudio(it) },
    )

    Spacer(modifier = Modifier.height(12.dp))

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      if (uiState.turns.isEmpty()) {
        Text(
            text = stringResource(R.string.translation_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(32.dp),
        )
      } else {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
          items(uiState.turns, key = { it.id }) { turn -> TurnBubble(turn) }
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))
    StatusPill(status = uiState.status, error = uiState.errorMessage)
    Spacer(modifier = Modifier.height(8.dp))
    SwitchButton(
        label =
            if (uiState.isActive) stringResource(R.string.translation_stop)
            else stringResource(R.string.translation_start),
        onClick = { if (uiState.isActive) viewModel.stop() else viewModel.start() },
        isDestructive = uiState.isActive,
    )
    Spacer(modifier = Modifier.height(16.dp))
  }

  if (pickerOpen) {
    LanguagePickerSheet(
        onPick = { picked ->
          viewModel.setTargetLanguage(picked)
          pickerOpen = false
        },
        onDismiss = { pickerOpen = false },
    )
  }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().height(56.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onBack) {
      Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(R.string.translation_back),
      )
    }
    Text(
        text = stringResource(R.string.translation_title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 8.dp),
    )
  }
}

@Composable
private fun TargetLanguageCard(
    language: LanguageOption,
    enabled: Boolean,
    onClick: () -> Unit,
) {
  Surface(
      modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onClick() },
      color = MaterialTheme.colorScheme.surfaceContainer,
      shape = MaterialTheme.shapes.large,
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = language.flagEmoji, style = MaterialTheme.typography.displaySmall)
      Spacer(modifier = Modifier.width(16.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = stringResource(R.string.translation_target_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = language.displayName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}

@Composable
private fun AudioRouteToggle(
    useGlasses: Boolean,
    enabled: Boolean,
    onSelect: (Boolean) -> Unit,
) {
  Surface(
      modifier = Modifier.fillMaxWidth(),
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      shape = MaterialTheme.shapes.large,
  ) {
    Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
      RouteSegment(
          icon = Icons.Default.Phone,
          label = stringResource(R.string.audio_route_phone),
          selected = !useGlasses,
          enabled = enabled,
          modifier = Modifier.weight(1f),
          onClick = { onSelect(false) },
      )
      RouteSegment(
          icon = Icons.Default.Sensors,
          label = stringResource(R.string.audio_route_glasses),
          selected = useGlasses,
          enabled = enabled,
          modifier = Modifier.weight(1f),
          onClick = { onSelect(true) },
      )
    }
  }
}

@Composable
private fun RouteSegment(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow
  val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
  Surface(
      modifier = modifier.clickable(enabled = enabled) { onClick() },
      color = bg,
      shape = MaterialTheme.shapes.medium,
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text(text = label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
  }
}

@Composable
private fun TurnBubble(turn: TranslationTurn) {
  Surface(
      modifier = Modifier.fillMaxWidth(),
      color = MaterialTheme.colorScheme.surfaceContainer,
      shape = MaterialTheme.shapes.large,
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (turn.sourceText.isNotEmpty()) {
        Text(
            text = turn.sourceText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (turn.translatedText.isNotEmpty()) {
        Text(
            text = turn.translatedText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
      } else if (turn.sourceText.isNotEmpty() && !turn.isFinal) {
        Text(
            text = "…",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

@Composable
private fun StatusPill(status: TranslationStatus, error: String?) {
  val (text, color) =
      when {
        error != null ->
            stringResource(R.string.translation_error_prefix, error) to MaterialTheme.colorScheme.error
        status == TranslationStatus.IDLE ->
            stringResource(R.string.translation_status_idle) to MaterialTheme.colorScheme.onSurfaceVariant
        status == TranslationStatus.STARTING ->
            stringResource(R.string.translation_status_starting) to MaterialTheme.colorScheme.primary
        status == TranslationStatus.LISTENING ->
            stringResource(R.string.translation_status_listening) to MaterialTheme.colorScheme.primary
        status == TranslationStatus.TRANSLATING ->
            stringResource(R.string.translation_status_translating) to MaterialTheme.colorScheme.tertiary
        else -> "" to MaterialTheme.colorScheme.onSurfaceVariant
      }

  Box(
      modifier =
          Modifier.fillMaxWidth()
              .background(
                  color = MaterialTheme.colorScheme.surfaceContainerLow,
                  shape = MaterialTheme.shapes.large,
              )
              .padding(horizontal = 16.dp, vertical = 10.dp),
      contentAlignment = Alignment.CenterStart,
  ) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
  }
}
