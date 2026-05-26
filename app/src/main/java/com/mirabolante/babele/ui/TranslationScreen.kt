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
import androidx.compose.material.icons.filled.SwapHoriz
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

private enum class LanguageSlot { X, Y }

@Composable
fun TranslationScreen(
    viewModel: TranslationViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var pickerOpen by remember { mutableStateOf<LanguageSlot?>(null) }
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

    LanguageRow(
        languageX = uiState.languageX,
        languageY = uiState.languageY,
        enabled = !uiState.isActive,
        onPickX = { pickerOpen = LanguageSlot.X },
        onPickY = { pickerOpen = LanguageSlot.Y },
        onSwap = { viewModel.swapLanguages() },
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

  pickerOpen?.let { slot ->
    LanguagePickerSheet(
        onPick = { picked ->
          when (slot) {
            LanguageSlot.X -> viewModel.setLanguageX(picked)
            LanguageSlot.Y -> viewModel.setLanguageY(picked)
          }
          pickerOpen = null
        },
        onDismiss = { pickerOpen = null },
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
private fun LanguageRow(
    languageX: LanguageOption,
    languageY: LanguageOption,
    enabled: Boolean,
    onPickX: () -> Unit,
    onPickY: () -> Unit,
    onSwap: () -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    LanguageTile(
        language = languageX,
        roleLabel = stringResource(R.string.translation_role_you),
        routeIcon = Icons.Default.Sensors,
        routeLabel = stringResource(R.string.audio_route_glasses),
        enabled = enabled,
        modifier = Modifier.weight(1f),
        onClick = onPickX,
    )
    IconButton(onClick = onSwap, enabled = enabled, modifier = Modifier.size(44.dp)) {
      Icon(
          imageVector = Icons.Default.SwapHoriz,
          contentDescription = stringResource(R.string.translation_swap_languages),
      )
    }
    LanguageTile(
        language = languageY,
        roleLabel = stringResource(R.string.translation_role_other),
        routeIcon = Icons.Default.Phone,
        routeLabel = stringResource(R.string.audio_route_phone),
        enabled = enabled,
        modifier = Modifier.weight(1f),
        onClick = onPickY,
    )
  }
}

@Composable
private fun LanguageTile(
    language: LanguageOption,
    roleLabel: String,
    routeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    routeLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Surface(
      modifier = modifier.clickable(enabled = enabled) { onClick() },
      color = MaterialTheme.colorScheme.surfaceContainer,
      shape = MaterialTheme.shapes.large,
  ) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
    ) {
      Text(
          text = roleLabel,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(text = language.flagEmoji, style = MaterialTheme.typography.displaySmall)
      Text(
          text = language.displayName,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = routeIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = routeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

@Composable
private fun TurnBubble(turn: TranslationTurn) {
  // User-spoken turns (X→Y, phone) align left; other-spoken (Y→X, glasses) align right.
  val alignEnd = !turn.spokenByUser
  val container =
      if (alignEnd) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceContainer
  val onContainer =
      if (alignEnd) MaterialTheme.colorScheme.onPrimaryContainer
      else MaterialTheme.colorScheme.onSurface
  Row(modifier = Modifier.fillMaxWidth()) {
    if (alignEnd) Spacer(modifier = Modifier.weight(0.12f))
    Surface(
        modifier = Modifier.weight(0.88f),
        color = container,
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
              color = onContainer.copy(alpha = 0.7f),
          )
        }
        if (turn.translatedText.isNotEmpty()) {
          Text(
              text = turn.translatedText,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = onContainer,
          )
        } else if (turn.sourceText.isNotEmpty() && !turn.isFinal) {
          Text(text = "…", style = MaterialTheme.typography.titleMedium, color = onContainer)
        }
      }
    }
    if (!alignEnd) Spacer(modifier = Modifier.weight(0.12f))
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
