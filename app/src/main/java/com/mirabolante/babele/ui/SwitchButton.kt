package com.mirabolante.babele.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SwitchButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
) {
  val containerColor =
      if (isDestructive) MaterialTheme.colorScheme.errorContainer
      else MaterialTheme.colorScheme.primary
  val contentColor =
      if (isDestructive) MaterialTheme.colorScheme.onErrorContainer
      else MaterialTheme.colorScheme.onPrimary
  Button(
      modifier = modifier.height(56.dp).fillMaxWidth(),
      onClick = onClick,
      shape = MaterialTheme.shapes.large,
      colors =
          ButtonDefaults.buttonColors(
              containerColor = containerColor,
              contentColor = contentColor,
              disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
              disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
          ),
      enabled = enabled,
  ) {
    Text(text = label, style = MaterialTheme.typography.labelLarge)
  }
}
