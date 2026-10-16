@file:OptIn(ExperimentalMaterial3Api::class)

package com.dawidggs.aliashub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dawidggs.aliashub.R
import com.dawidggs.aliashub.data.Alias
import com.dawidggs.aliashub.data.CreateOptions
import com.dawidggs.aliashub.data.CreateRequest
import com.dawidggs.aliashub.data.ProviderType

/**
 * "New alias" bottom sheet. Provider and domain are picked from select boxes; the name field
 * appears only for domains that take a custom name, and is mandatory only when the domain demands it.
 */
@Composable
fun CreateAliasSheet(
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

    // Options depend on the account and (SimpleLogin) carry fresh signed values, so they are
    // fetched whenever the provider changes and every time the sheet opens.
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
    val nameMissing = chosen?.prefixRequired == true && prefix.isBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.new_alias), style = MaterialTheme.typography.titleLarge)

            if (providers.size > 1) {
                SelectField(
                    label = stringResource(R.string.provider),
                    selected = selected,
                    options = providers,
                    optionLabel = { it.label },
                    onSelect = { selected = it },
                )
            }

            if (loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
            } else {
                if (domains.size > 1) {
                    SelectField(
                        label = stringResource(R.string.domain),
                        selected = chosen,
                        options = domains,
                        optionLabel = { it.label },
                        onSelect = { domainId = it.id },
                    )
                }
                if (chosen?.needsPrefix == true) {
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { prefix = it },
                        label = {
                            Text(stringResource(if (chosen.prefixRequired) R.string.name else R.string.name_optional))
                        },
                        supportingText = {
                            Text(
                                if (nameMissing) {
                                    stringResource(R.string.custom_name_required_text)
                                } else {
                                    stringResource(
                                        R.string.alias_creator_result,
                                        prefix.trim().ifEmpty { stringResource(R.string.random) },
                                        chosen.joiner,
                                        chosen.label,
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

            Button(
                enabled = !loading && !nameMissing,
                onClick = {
                    // A name typed for one domain must not leak into a domain that doesn't use one.
                    val name = if (chosen?.needsPrefix == true) prefix.trim() else ""
                    onCreate(selected, CreateRequest(note.trim(), domainId, name))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.create_button)) }
        }
    }
}

/** Read-only outlined field that opens a dropdown menu; a drop-in "select box". */
@Composable
private fun <T> SelectField(
    label: String,
    selected: T?,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var widthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Box(Modifier.fillMaxWidth().onSizeChanged { widthPx = it.width }) {
        OutlinedTextField(
            value = selected?.let(optionLabel).orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent layer on top so the whole field is one tap target (a read-only text field
        // would otherwise swallow the click for its own cursor handling).
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(with(density) { widthPx.toDp() })
                .heightIn(max = 320.dp),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = {
                        if (option == selected) Icon(Icons.Default.Check, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
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
