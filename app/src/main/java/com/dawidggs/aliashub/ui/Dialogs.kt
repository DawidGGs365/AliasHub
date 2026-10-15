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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dawidggs.aliashub.data.Alias
import com.dawidggs.aliashub.data.CreateOptions
import com.dawidggs.aliashub.data.CreateRequest
import com.dawidggs.aliashub.data.ProviderType
import androidx.compose.ui.tooling.preview.Preview
import com.dawidggs.aliashub.R

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
        title = { Text(stringResource(R.string.new_alias)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (providers.size > 1) {
                    SectionLabel(stringResource(R.string.provider))
                    providers.forEach { type ->
                        RadioRow(type.label, type == selected) { selected = type }
                    }
                }
                if (loading) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                } else {
                    if (domains.size > 1) {
                        SectionLabel(stringResource(R.string.domain))
                        domains.forEach { d -> RadioRow(d.label, d.id == domainId) { domainId = d.id } }
                    }
                    if (chosen?.needsPrefix == true) {
                        OutlinedTextField(
                            value = prefix,
                            onValueChange = { prefix = it },
                            label = { Text(if (chosen.prefixRequired) stringResource(R.string.name) else stringResource(
                                R.string.name_optional
                            )) },
                            supportingText = {
                                Text(
                                    if (chosen.prefixRequired && prefix.isBlank()) {
                                        stringResource(R.string.custom_name_required_text)
                                    } else {
                                        stringResource(
                                            R.string.alias_creator_result,
                                            prefix.trim().ifEmpty { stringResource(R.string.random) },
                                            chosen.joiner,
                                            chosen.label
                                        )
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
                    label = { Text(stringResource(R.string.note)) },
                    placeholder = { Text(stringResource(R.string.note_placeholder)) },
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
            ) { Text(stringResource(R.string.create_button)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) } },
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
        title = { Text(stringResource(R.string.edit_note)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(alias.address)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(note.trim()) }) { Text(stringResource(R.string.save_button)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) } },
    )
}

@Composable
fun ConfirmDeleteDialog(alias: Alias, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_alias_confirmation)) },
        text = { Text(stringResource(R.string.delete_alias_warning_text, alias.address)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete_button)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) } },
    )
}
