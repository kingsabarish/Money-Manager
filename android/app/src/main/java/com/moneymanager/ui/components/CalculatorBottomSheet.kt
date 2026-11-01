package com.moneymanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.R
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorBottomSheet(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Clean initial value (remove currency symbols if any, take valid decimal part)
    val cleanedInitial = remember(initialValue) {
        val filtered = initialValue.trim().filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } <= 1) filtered else ""
    }

    var expression by remember { mutableStateOf(cleanedInitial) }
    val displayScrollState = rememberScrollState()

    // Scroll to the end of the expression whenever it changes
    LaunchedEffect(expression) {
        displayScrollState.animateScrollTo(displayScrollState.maxValue)
    }

    val liveResult: BigDecimal? = remember(expression) {
        if (expression.isBlank()) null
        else CalculatorEngine.evaluatePartial(expression)
    }

    val isDivisionByZero = remember(expression) {
        expression.contains("÷ 0") || expression.contains("/ 0")
    }

    val displayResult = when {
        isDivisionByZero -> "Cannot divide by 0"
        liveResult != null -> "= ${CalculatorEngine.formatResult(liveResult)}"
        else -> ""
    }

    fun onDigit(d: Char) {
        expression = when {
            expression == "0" -> d.toString()
            else -> expression + d
        }
    }

    fun onDecimal() {
        val lastToken = expression.split(" ", "(", ")").lastOrNull().orEmpty()
        if (!lastToken.contains('.')) {
            expression = if (lastToken.isEmpty() || expression.endsWith(" ")) {
                expression + "0."
            } else {
                expression + "."
            }
        }
    }

    fun onOperator(op: String) {
        val trimmed = expression.trimEnd()
        expression = when {
            trimmed.isEmpty() -> if (op == "−") "-" else ""
            expression.endsWith(" ") -> expression.dropLast(3) + " $op "
            else -> "$expression $op "
        }
    }

    fun onOpenParen() {
        expression = when {
            expression.isEmpty() || expression.endsWith(" ") || expression.endsWith("(") -> {
                "$expression("
            }
            else -> "$expression × ("
        }
    }

    fun onCloseParen() {
        val openCount = expression.count { it == '(' }
        val closeCount = expression.count { it == ')' }
        if (openCount > closeCount && (expression.lastOrNull()?.isDigit() == true || expression.endsWith(")"))) {
            expression = "$expression)"
        }
    }

    fun onBackspace() {
        expression = when {
            expression.endsWith(" ") -> expression.dropLast(3)
            expression.isNotEmpty() -> expression.dropLast(1)
            else -> ""
        }
    }

    fun onClear() {
        expression = ""
    }

    fun onEquals() {
        val result = CalculatorEngine.evaluate(CalculatorEngine.prepareForEvaluation(expression))
        if (result != null) {
            expression = CalculatorEngine.formatResult(result)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Calculator",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onDismiss) {
                    Text("✕", style = MaterialTheme.typography.titleMedium)
                }
            }

            // Display area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(displayScrollState),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = if (expression.isEmpty()) "0" else expression,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = displayResult,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDivisionByZero) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        minLines = 1,
                    )
                }
            }

            // Keypad
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Row 1: C, (, ), ÷
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CalcButton("C", Modifier.weight(1f), isSpecial = true, onClick = ::onClear)
                    CalcButton("(", Modifier.weight(1f), isOperator = true, onClick = ::onOpenParen)
                    CalcButton(")", Modifier.weight(1f), isOperator = true, onClick = ::onCloseParen)
                    CalcButton("÷", Modifier.weight(1f), isOperator = true, onClick = { onOperator("÷") })
                }

                // Row 2: 7, 8, 9, ×
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CalcButton("7", Modifier.weight(1f), onClick = { onDigit('7') })
                    CalcButton("8", Modifier.weight(1f), onClick = { onDigit('8') })
                    CalcButton("9", Modifier.weight(1f), onClick = { onDigit('9') })
                    CalcButton("×", Modifier.weight(1f), isOperator = true, onClick = { onOperator("×") })
                }

                // Row 3: 4, 5, 6, −
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CalcButton("4", Modifier.weight(1f), onClick = { onDigit('4') })
                    CalcButton("5", Modifier.weight(1f), onClick = { onDigit('5') })
                    CalcButton("6", Modifier.weight(1f), onClick = { onDigit('6') })
                    CalcButton("−", Modifier.weight(1f), isOperator = true, onClick = { onOperator("−") })
                }

                // Row 4: 1, 2, 3, +
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CalcButton("1", Modifier.weight(1f), onClick = { onDigit('1') })
                    CalcButton("2", Modifier.weight(1f), onClick = { onDigit('2') })
                    CalcButton("3", Modifier.weight(1f), onClick = { onDigit('3') })
                    CalcButton("+", Modifier.weight(1f), isOperator = true, onClick = { onOperator("+") })
                }

                // Row 5: 0, ., ⌫, =
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CalcButton("0", Modifier.weight(1f), onClick = { onDigit('0') })
                    CalcButton(".", Modifier.weight(1f), onClick = ::onDecimal)
                    CalcButton("⌫", Modifier.weight(1f), isSpecial = true, onClick = ::onBackspace)
                    CalcButton("=", Modifier.weight(1f), isPrimary = true, onClick = ::onEquals)
                }
            }

            // Bottom Actions (Cancel / Done)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val finalResult = when {
                            liveResult != null -> CalculatorEngine.formatResult(liveResult)
                            expression.isNotBlank() -> expression
                            else -> "0"
                        }
                        onConfirm(finalResult)
                    },
                    enabled = !isDivisionByZero,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun CalcButton(
    text: String,
    modifier: Modifier = Modifier,
    isOperator: Boolean = false,
    isSpecial: Boolean = false,
    isPrimary: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = when {
        isPrimary -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
        isOperator -> ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        isSpecial -> ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.error,
        )
        else -> ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = colors,
    ) {
        Text(
            text = text,
            fontSize = if (text.length > 1) 18.sp else 20.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

