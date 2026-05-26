package com.mirabolante.babele.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mirabolante.babele.R
import com.mirabolante.babele.translation.AudioMode

private enum class HomeStep { CHOICE, GLASSES_SETUP }

@Composable
fun HomeScreen(
    onStart: (AudioMode) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
  var step by remember { mutableStateOf(HomeStep.CHOICE) }

  when (step) {
    HomeStep.CHOICE ->
        ChoiceStep(
            modifier = modifier,
            onGlasses = { step = HomeStep.GLASSES_SETUP },
            onPhone = { onStart(AudioMode.PHONE) },
            onOpenSettings = onOpenSettings,
        )
    HomeStep.GLASSES_SETUP ->
        GlassesSetupStep(
            modifier = modifier,
            onBack = { step = HomeStep.CHOICE },
            onStart = { onStart(AudioMode.GLASSES) },
        )
  }
}

@Composable
private fun ChoiceStep(
    onGlasses: () -> Unit,
    onPhone: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .statusBarsPadding()
              .navigationBarsPadding()
              .padding(horizontal = 24.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      IconButton(onClick = onOpenSettings) {
        Icon(
            imageVector = Icons.Default.Key,
            contentDescription = stringResource(R.string.key_settings),
        )
      }
    }

    Spacer(modifier = Modifier.weight(1f))

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

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = stringResource(R.string.home_choose_mode),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth(),
    )
    ModeCard(
        icon = Icons.Default.Sensors,
        title = stringResource(R.string.home_mode_glasses_title),
        body = stringResource(R.string.home_mode_glasses_body),
        onClick = onGlasses,
    )
    ModeCard(
        icon = Icons.Default.Phone,
        title = stringResource(R.string.home_mode_phone_title),
        body = stringResource(R.string.home_mode_phone_body),
        onClick = onPhone,
    )

    Spacer(modifier = Modifier.weight(1f))
  }
}

@Composable
private fun GlassesSetupStep(
    onBack: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .statusBarsPadding()
              .navigationBarsPadding()
              .padding(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.home_back),
        )
      }
      Text(
          text = stringResource(R.string.home_glasses_setup_title),
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(start = 8.dp),
      )
    }

    Spacer(modifier = Modifier.weight(1f))

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

    Spacer(modifier = Modifier.weight(1f))

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
    SwitchButton(label = stringResource(R.string.home_continue), onClick = onStart)
  }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Surface(
      modifier = modifier.fillMaxWidth().clickable { onClick() },
      color = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      shape = MaterialTheme.shapes.large,
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(28.dp),
      )
      Spacer(modifier = Modifier.width(16.dp))
      Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
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
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
      Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(28.dp),
      )
      Spacer(modifier = Modifier.width(16.dp))
      Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
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
