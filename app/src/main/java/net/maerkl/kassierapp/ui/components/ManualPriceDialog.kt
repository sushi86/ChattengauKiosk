package net.maerkl.kassierapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.maerkl.kassierapp.ui.theme.Green900

@Composable
fun ManualPriceDialog(
    onDismiss: () -> Unit,
    onConfirm: (price: Double, name: String) -> Unit
) {
    var priceInput by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }

    val displayPrice = formatPriceInput(priceInput)
    val priceValue = priceInput.toDoubleOrNull()?.let { it / 100.0 } ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Freier Preis") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Price display
                Text(
                    text = displayPrice,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )

                // Numpad grid
                val buttons = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "\u232B")
                )

                buttons.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { label ->
                            OutlinedButton(
                                onClick = {
                                    when (label) {
                                        "C" -> priceInput = ""
                                        "\u232B" -> {
                                            if (priceInput.isNotEmpty()) {
                                                priceInput = priceInput.dropLast(1)
                                            }
                                        }
                                        else -> {
                                            if (priceInput.length < 7) {
                                                priceInput += label
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                            ) {
                                Text(label, fontSize = 20.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Optional name field
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Bezeichnung (optional)") },
                    placeholder = { Text("Freier Preis") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val name = customName.trim().ifEmpty { "Freier Preis" }
                    onConfirm(priceValue, name)
                },
                enabled = priceValue > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Green900)
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

private fun formatPriceInput(input: String): String {
    if (input.isEmpty()) return "0,00 \u20AC"
    val cents = input.toLongOrNull() ?: 0L
    val euros = cents / 100
    val remainingCents = cents % 100
    return String.format("%d,%02d \u20AC", euros, remainingCents)
}
