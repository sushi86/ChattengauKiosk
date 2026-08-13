package net.maerkl.kassierapp.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties

@Composable
fun FreierPreisDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, preisCent: Long, taxRate: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var preisCent by remember { mutableStateOf(0L) }
    var taxRate by remember { mutableStateOf(19) }

    val confirmEnabled = preisCent > 0L

    AlertDialog(
        onDismissRequest = { /* explizites Schließen nur via Abbrechen/Hinzufügen */ },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true,
        ),
        title = { Text("Freier Preis") },
        text = {
            Column(modifier = Modifier.width(360.dp)) {
                Text(
                    text = formatCents(preisCent),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )

                Numpad(
                    onDigit = { d ->
                        val next = preisCent * 10 + d
                        if (next <= 1_000_000_00L) preisCent = next
                    },
                    onBackspace = { preisCent /= 10 },
                    onClear = { preisCent = 0L },
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    label = { Text("Bezeichnung") },
                    placeholder = { Text("Freier Preis") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )

                Text(
                    "MwSt.",
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 7, 19).forEach { rate ->
                        FilterChip(
                            selected = taxRate == rate,
                            onClick = { taxRate = rate },
                            label = { Text("$rate %") },
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, preisCent, taxRate) },
                enabled = confirmEnabled,
            ) { Text("Hinzufügen") }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Abbrechen", fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun Numpad(
    onDigit: (Long) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "⌫"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { label ->
                    NumpadButton(
                        label = label,
                        modifier = Modifier.weight(1f).aspectRatio(1.4f),
                        onClick = {
                            when (label) {
                                "C" -> onClear()
                                "⌫" -> onBackspace()
                                else -> onDigit(label.toLong())
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NumpadButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatCents(cents: Long): String {
    val euros = cents / 100
    val rest = cents % 100
    return "%d,%02d €".format(euros, rest)
}
