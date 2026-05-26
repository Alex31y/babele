package com.mirabolante.babele.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mirabolante.babele.R

@Composable
fun HomeScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  val context = LocalContext.current

  Column(
      modifier =
          modifier
              .fillMaxSize()
              .statusBarsPadding()
              .navigationBarsPadding()
              .verticalScroll(scrollState)
              .padding(horizontal = 24.dp, vertical = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Spacer(modifier = Modifier.height(8.dp))

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
          imageVector = Icons.Default.Translate,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(72.dp),
      )
      Text(
          text = stringResource(R.string.home_title),
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center,
      )
      Text(
          text = stringResource(R.string.home_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
      TipCard(
          icon = Icons.Default.Bluetooth,
          title = stringResource(R.string.home_step_pair_title),
          body = stringResource(R.string.home_step_pair_body),
      )
      TipCard(
          icon = Icons.Default.Translate,
          title = stringResource(R.string.home_step_languages_title),
          body = stringResource(R.string.home_step_languages_body),
      )
      TipCard(
          icon = Icons.Default.Mic,
          title = stringResource(R.string.home_step_talk_title),
          body = stringResource(R.string.home_step_talk_body),
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedButton(
        onClick = {
          context.startActivity(
              Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          )
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = MaterialTheme.shapes.large,
    ) {
      Icon(imageVector = Icons.Default.Bluetooth, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
      Text(stringResource(R.string.home_open_bt_settings))
    }

    SwitchButton(
        label = stringResource(R.string.home_continue),
        onClick = onContinue,
    )
  }
}

@Composable
private fun TipCard(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
  Surface(
      modifier = modifier.fillMaxWidth(),
      color = MaterialTheme.colorScheme.surfaceContainer,
      contentColor = MaterialTheme.colorScheme.onSurface,
      shape = MaterialTheme.shapes.large,
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
      Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(28.dp),
      )
      Spacer(modifier = Modifier.width(16.dp))
      Column(
          verticalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
