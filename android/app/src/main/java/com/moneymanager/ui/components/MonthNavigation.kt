package com.moneymanager.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Detects a horizontal swipe for month navigation: swipe right → [onPrevious]
 * month, swipe left → [onNext] month (matching the on-screen ‹ › arrows). Only
 * fires once the accumulated drag passes a threshold, so it doesn't fight the
 * vertical scrolling of a list underneath it.
 */
fun Modifier.monthSwipe(onPrevious: () -> Unit, onNext: () -> Unit): Modifier =
    this.pointerInput(onPrevious, onNext) {
        val threshold = 60.dp.toPx()
        var totalDrag = 0f
        var fired = false
        detectHorizontalDragGestures(
            onDragStart = {
                totalDrag = 0f
                fired = false
            },
            onHorizontalDrag = { change, dragAmount ->
                totalDrag += dragAmount
                if (!fired && totalDrag > threshold) {
                    onPrevious()
                    fired = true
                } else if (!fired && totalDrag < -threshold) {
                    onNext()
                    fired = true
                }
                if (fired) change.consume()
            },
        )
    }

/**
 * A month header row: ‹ [label] › with an optional [subtitle] line (e.g. the
 * month total) centered beneath it.
 */
@Composable
fun MonthHeader(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onPrevious) {
                Text("‹", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onNext) {
                Text("›", style = MaterialTheme.typography.titleLarge)
            }
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
        }
    }
}
