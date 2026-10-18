@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dawidggs.aliashub.R
import com.dawidggs.aliashub.data.CreateOptions
import com.dawidggs.aliashub.data.CreateRequest
import com.dawidggs.aliashub.data.GeneratorOption
import com.dawidggs.aliashub.data.ProviderType

/**
 * "New alias" bottom sheet. The provider is already chosen (from the FAB menu), so only the
 * domain is picked from a select box; the name field appears only for domains that take a custom
 * name, and is mandatory only when the domain demands it.
 */
@Composable
fun CreateAliasSheet(
    provider: ProviderType,
    loadOptions: suspend (ProviderType, String?) -> Pair<CreateOptions?, String?>,
    onDismiss: () -> Unit,
    onCreate: (ProviderType, CreateRequest) -> Unit,
) {
    var note by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }
    var options by remember { mutableStateOf<CreateOptions?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var domainId by remember { mutableStateOf<String?>(null) }
    var generatorId by remember { mutableStateOf<String?>(null) }
    // Random aliases pick from their own domain list (SimpleLogin), custom ones from [domainId].
    var randomDomainId by remember { mutableStateOf<String?>(null) }
    var styleId by remember { mutableStateOf<String?>(null) }
    var reloading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Options depend on the account and (SimpleLogin) carry fresh signed values, so they are
    // fetched every time the sheet opens.
    LaunchedEffect(provider) {
        loading = true
        options = null
        loadError = null
        domainId = null
        generatorId = null
        randomDomainId = null
        styleId = null
        val (loaded, error) = loadOptions(provider, null)
        options = loaded
        loadError = error
        domainId = loaded?.defaultId ?: loaded?.domains?.firstOrNull()?.id
        generatorId = loaded?.defaultGeneratorId
        randomDomainId = loaded?.defaultRandomDomainId
        styleId = loaded?.suffixStyleId
        loading = false
    }

    val domains = options?.domains.orEmpty()
    val chosen = domains.firstOrNull { it.id == domainId }

    // Providers with several ways to name an alias (generators) get a "Generator" select; the
    // domain and name fields then follow the chosen generator. A domain that only accepts custom
    // names (Addy usernames) leaves "custom" as the one choice. Without generators (Relay) the
    // old per-domain rules apply.
    val allGenerators = options?.generators.orEmpty()
    // A domain that takes no name (Addy shared domains on a free plan) can't use the custom generator.
    val generators = when {
        chosen?.prefixRequired == true -> allGenerators.filter { it.takesName }
        chosen != null && !chosen.needsPrefix -> allGenerators.filter { !it.takesName }.ifEmpty { allGenerators }
        else -> allGenerators
    }
    val generator: GeneratorOption? = generators.firstOrNull { it.id == generatorId } ?: generators.firstOrNull()
    val randomMode = generator?.randomDomain == true
    val activeDomains = if (randomMode) options?.randomDomains.orEmpty() else domains
    val activeDomainId = if (randomMode) randomDomainId else domainId
    val showDomain = activeDomains.size > 1 && (generator?.takesDomain ?: true)
    val styles = options?.suffixStyles.orEmpty()
    // UUID aliases are generated whole by the server and never have a suffix, so no style there.
    val showStyle = styles.size > 1 && generator?.id != "uuid"
    val showName = generator?.takesName ?: (chosen?.needsPrefix == true)
    val nameRequired = generator?.takesName ?: (chosen?.prefixRequired == true)
    val nameMissing = nameRequired && prefix.isBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Opens straight to full height (no half-open stop), same as skipPartiallyExpanded did.
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
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
            Column {
                Text(stringResource(R.string.new_alias), style = MaterialTheme.typography.titleLarge)
                Text(
                    provider.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            } else {
                if (generators.size > 1) {
                    SelectField(
                        label = stringResource(R.string.generator),
                        selected = generator,
                        options = generators,
                        optionLabel = { it.label },
                        onSelect = { generatorId = it.id },
                    )
                }
                if (showDomain) {
                    SelectField(
                        label = stringResource(R.string.domain),
                        selected = activeDomains.firstOrNull { it.id == activeDomainId },
                        options = activeDomains,
                        optionLabel = { it.label },
                        onSelect = { if (randomMode) randomDomainId = it.id else domainId = it.id },
                    )
                }
                if (showStyle) {
                    SelectField(
                        label = stringResource(R.string.suffix_style),
                        selected = styles.firstOrNull { it.id == styleId },
                        options = styles,
                        optionLabel = { it.label },
                        onSelect = { picked ->
                            if (picked.id != styleId && !reloading) {
                                val previous = styleId
                                styleId = picked.id
                                reloading = true
                                scope.launch {
                                    // The suffixes are generated server-side per style, so fetch them again.
                                    val (loaded, error) = loadOptions(provider, picked.id)
                                    if (loaded != null) {
                                        options = loaded
                                        domainId = loaded.defaultId
                                        styleId = loaded.suffixStyleId
                                    } else {
                                        styleId = previous // keep the shown style in sync with the loaded suffixes
                                    }
                                    loadError = error
                                    reloading = false
                                }
                            }
                        },
                    )
                }
                if (showName && chosen != null) {
                    TextField(
                        value = prefix,
                        onValueChange = { prefix = it },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                        shape = RoundedCornerShape(16.dp),
                        label = {
                            Text(stringResource(if (nameRequired) R.string.name else R.string.name_optional))
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

            TextField(
                value = note,
                onValueChange = { note = it },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                shape = RoundedCornerShape(16.dp),
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
                enabled = !loading && !reloading && !nameMissing,
                shapes = ButtonDefaults.shapes(),
                onClick = {
                    // A name typed for one domain must not leak into a domain that doesn't use one.
                    val name = if (showName) prefix.trim() else ""
                    onCreate(provider, CreateRequest(note.trim(), activeDomainId, name, generator?.id, styleId.takeIf { showStyle }))
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
    var widthPx by remember { mutableIntStateOf(0) }
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
            shape = RoundedCornerShape(20.dp),
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
