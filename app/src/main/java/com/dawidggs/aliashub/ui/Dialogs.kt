package com.dawidggs.aliashub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dawidggs.aliashub.data.Alias
import com.dawidggs.aliashub.data.CreateOptions
import com.dawidggs.aliashub.data.CreateRequest
import com.dawidggs.aliashub.data.ProviderType
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun CreateAliasDialog(
    providers: List<ProviderType>,
    initial: ProviderType?,
    loadOptions: suspend (ProviderType) -> Pair<CreateOptions?, String?>,
    onDismiss: () -> Unit,
    onCreate: (ProviderType, CreateRequest) -> Unit,
) {
    var selected by remember { mutableStateOf(initial ?: providers.first()) }
    var note by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }
    var options by remember { mutableStateOf<CreateOptions?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var domainId by remember { mutableStateOf<String?>(null) }

    // Options are re-fetched whenever the provider changes (and each time the dialog opens),
    // because they depend on the account and, for SimpleLogin, carry fresh signed values.
    LaunchedEffect(selected) {
        loading = true
        options = null
        loadError = null
        domainId = null
        val (loaded, error) = loadOptions(selected)
        options = loaded
        loadError = error
        domainId = loaded?.defaultId ?: loaded?.domains?.firstOrNull()?.id
        loading = false
    }

    val domains = options?.domains.orEmpty()
    val chosen = domains.firstOrNull { it.id == domainId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New alias") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (providers.size > 1) {
                    SectionLabel("Provider")
                    providers.forEach { type ->
                        RadioRow(type.label, type == selected) { selected = type }
                    }
                }
                if (loading) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                } else {
                    if (domains.size > 1) {
                        SectionLabel("Domain")
                        domains.forEach { d -> RadioRow(d.label, d.id == domainId) { domainId = d.id } }
                    }
                    if (chosen?.needsPrefix == true) {
                        OutlinedTextField(
                            value = prefix,
                            onValueChange = { prefix = it },
                            label = { Text(if (chosen.prefixRequired) "Name" else "Name (optional)") },
                            supportingText = {
                                Text(
                                    if (chosen.prefixRequired && prefix.isBlank()) {
                                        "This domain needs a name of your choosing."
                                    } else {
                                        "Result: ${prefix.trim().ifEmpty { "random" }}${chosen.joiner}${chosen.label}"
                                    },
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("Where will you use it?") },
                    modifier = Modifier.fillMaxWidth(),
                )
                options?.warning?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                loadError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            val nameMissing = chosen?.prefixRequired == true && prefix.isBlank()
            TextButton(
                enabled = !loading && !nameMissing,
                onClick = {
                    // A name typed for one domain must not leak into a domain that doesn't use one.
                    val name = if (chosen?.needsPrefix == true) prefix.trim() else ""
                    onCreate(selected, CreateRequest(note.trim(), domainId, name))
                },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
fun NoteDialog(alias: Alias, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var note by remember { mutableStateOf(alias.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(alias.address)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(note.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDeleteDialog(alias: Alias, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete alias?") },
        text = { Text("${alias.address} will stop receiving mail. This can't be undone from here.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
