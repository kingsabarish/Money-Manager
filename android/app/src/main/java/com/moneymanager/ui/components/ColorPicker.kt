package com.moneymanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A full HSV color picker in a dialog: a saturation/value panel plus a hue bar,
 * so the user can pick any RGB color. Reports the chosen [Color] via [onConfirm].
 */
@Composable
fun ColorPickerDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    val hsv = remember(initial) { initial.toHsv() }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }

    val selected = Color.hsv(hue, saturation, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SaturationValuePanel(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onChange = { s, v ->
                        saturation = s
                        value = v
                    },
                )
                HueBar(hue = hue, onHueChange = { hue = it })
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(selected)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(8.dp),
                                ),
                    )
                    Text(
                        text = selected.toHexString(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("Use color") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** The 2D saturation (x) / value (y) selection panel for the current [hue]. */
@Composable
private fun SaturationValuePanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (saturation: Float, value: Float) -> Unit,
) {
    val hueColor = Color.hsv(hue, 1f, 1f)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.3f)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectTapGestures { pos -> onChange(satOf(pos, size.width), valueOf(pos, size.height)) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onChange(satOf(change.position, size.width), valueOf(change.position, size.height))
                    }
                },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val cx = saturation * size.width
            val cy = (1f - value) * size.height
            drawCircle(color = Color.White, radius = 10f, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            drawCircle(color = Color.Black, radius = 13f, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
        }
    }
}

/** The horizontal hue spectrum bar (0..360°). */
@Composable
private fun HueBar(hue: Float, onHueChange: (Float) -> Unit) {
    val spectrum =
        remember {
            (0..360 step 60).map { Color.hsv(it.toFloat().coerceAtMost(359f), 1f, 1f) }
        }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectTapGestures { pos -> onHueChange(hueOf(pos, size.width)) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onHueChange(hueOf(change.position, size.width))
                    }
                },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(Brush.horizontalGradient(spectrum))
            val x = (hue / 360f) * size.width
            drawLine(
                color = Color.White,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 4f,
            )
        }
    }
}

private fun satOf(pos: Offset, width: Int): Float =
    if (width <= 0) 0f else (pos.x / width).coerceIn(0f, 1f)

private fun valueOf(pos: Offset, height: Int): Float =
    if (height <= 0) 0f else (1f - pos.y / height).coerceIn(0f, 1f)

private fun hueOf(pos: Offset, width: Int): Float =
    if (width <= 0) 0f else ((pos.x / width) * 360f).coerceIn(0f, 360f)

/** HSV components [hue 0..360, saturation 0..1, value 0..1] of this color. */
private fun Color.toHsv(): FloatArray {
    val out = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), out)
    return out
}

private fun Color.toHexString(): String {
    val argb = toArgb()
    return String.format(Locale.US, "#%06X", 0xFFFFFF and argb)
}

/** Convenience: the ARGB int for the color, opaque. */
fun Color.toOpaqueArgb(): Int = (0xFF000000.toInt()) or (toArgb() and 0x00FFFFFF)

/** Round a 0..1 fraction to a percentage int for display. */
fun Float.toPercentInt(): Int = (this * 100).roundToInt()
