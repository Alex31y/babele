package com.mirabolante.babele.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mirabolante.babele.R

private const val AI_STUDIO_URL = "https://aistudio.google.com/apikey"

@Composable
fun ApiKeyScreen(
    initialKey: String,
    forced: Boolean,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scroll = rememberScrollState()
  var key by remember { mutableStateOf(initialKey) }
  var showInvalid by remember { mutableStateOf(false) }

  Column(
      modifier =
          modifier
              .fillMaxSize()
              .statusBarsPadding()
              .navigationBarsPadding()
              .verticalScroll(scroll)
              .padding(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
          imageVector = Icons.Default.Key,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(28.dp),
      )
      Text(
          text = stringResource(R.string.key_title),
          style = MaterialTheme.typography.headlineSmall,
          modifier = Modifier.padding(start = 12.dp).weight(1f),
      )
      if (!forced) {
        IconButton(onClick = onClose) {
          Icon(
              imageVector = Icons.Default.Close,
              contentDescription = stringResource(R.string.key_close),
          )
        }
      }
    }

    Text(
        text = stringResource(R.string.key_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
          text = stringResource(R.string.key_what_is),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(16.dp),
      )
    }

    Text(
        text = stringResource(R.string.key_steps_title),
        style = MaterialTheme.typography.titleMedium,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(stringResource(R.string.key_step1), style = MaterialTheme.typography.bodyMedium)
      Text(stringResource(R.string.key_step2), style = MaterialTheme.typography.bodyMedium)
      Text(stringResource(R.string.key_step3), style = MaterialTheme.typography.bodyMedium)
      Text(stringResource(R.string.key_step4), style = MaterialTheme.typography.bodyMedium)
    }

    OutlinedButton(
        onClick = {
          context.startActivity(
              Intent(Intent.ACTION_VIEW, Uri.parse(AI_STUDIO_URL))
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          )
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = MaterialTheme.shapes.large,
    ) {
      Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
      Text(stringResource(R.string.key_get_button))
    }

    OutlinedTextField(
        value = key,
        onValueChange = {
          key = it
          showInvalid = false
        },
        label = { Text(stringResource(R.string.key_field_label)) },
        placeholder = { Text(stringResource(R.string.key_field_placeholder)) },
        singleLine = true,
        isError = showInvalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    if (showInvalid) {
      Text(
          text = stringResource(R.string.key_invalid),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
      )
    }

    Text(
        text = stringResource(R.string.key_privacy),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    SwitchButton(
        label = stringResource(R.string.key_save),
        enabled = key.isNotBlank(),
        onClick = {
          val trimmed = key.trim()
          if (!trimmed.startsWith("AIza")) {
            showInvalid = true
          } else {
            onSave(trimmed)
          }
        },
    )

    if (!forced) {
      TextButton(
          onClick = {
            key = ""
            onClear()
          },
          modifier = Modifier.fillMaxWidth(),
      ) {
        Text(stringResource(R.string.key_clear), color = MaterialTheme.colorScheme.error)
      }
    }

    Spacer(modifier = Modifier.height(8.dp))
  }
}
