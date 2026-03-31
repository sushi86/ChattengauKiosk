package net.maerkl.kassierapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.maerkl.kassierapp.data.local.Article

@Composable
fun ArticleDialog(
    article: Article? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, price: Double, emoji: String, isActive: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(article?.name ?: "") }
    var priceText by remember { mutableStateOf(article?.let { String.format("%.2f", it.price) } ?: "") }
    var emoji by remember { mutableStateOf(article?.emoji ?: "") }
    var isActive by remember { mutableStateOf(article?.isActive ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (article != null) "Artikel bearbeiten" else "Artikel hinzufügen") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Preis") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it },
                    label = { Text("Emoji") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isActive, onCheckedChange = { isActive = it })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Aktiv")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val price = priceText.replace(",", ".").toDoubleOrNull()
                    if (name.isNotBlank() && price != null && price > 0) {
                        onSave(name, price, emoji, isActive)
                    }
                }
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
