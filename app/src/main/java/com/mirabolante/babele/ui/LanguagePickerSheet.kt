package com.mirabolante.babele.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import com.mirabolante.babele.R
import com.mirabolante.babele.translation.LanguageOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerSheet(
    onPick: (LanguageOption) -> Unit,
    onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
      onDismissRequest = onDismiss,
      sheetState = sheetState,
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
          text = stringResource(R.string.language_picker_title),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(horizontal = 8.dp),
      )
      LazyVerticalGrid(
          columns = GridCells.Fixed(3),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
      ) {
        items(LanguageOption.ALL) { option ->
          Surface(
              modifier =
                  Modifier.fillMaxWidth()
                      .aspectRatio(1.4f)
                      .clickable { onPick(option) },
              color = MaterialTheme.colorScheme.surfaceContainer,
              shape = MaterialTheme.shapes.medium,
          ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            ) {
              Text(text = option.flagEmoji, style = MaterialTheme.typography.headlineMedium)
              Text(
                  text = option.displayName,
                  style = MaterialTheme.typography.labelMedium,
                  textAlign = TextAlign.Center,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
              )
            }
          }
        }
      }
    }
  }
}
