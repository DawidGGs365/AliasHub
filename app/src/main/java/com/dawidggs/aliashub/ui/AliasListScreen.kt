@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dawidggs.aliashub.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.dawidggs.aliashub.AliasViewModel
import com.dawidggs.aliashub.UiState
import com.dawidggs.aliashub.data.Alias
import com.dawidggs.aliashub.R
import androidx.compose.ui.res.stringResource
import com.dawidggs.aliashub.data.ProviderType

@Composable
fun AliasListScreen(state: UiState, vm: AliasViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val copy = stringResource(R.string.copy)
    var showCreate by remember { mutableStateOf(false) }
    var deleteFor by remember { mutableStateOf<Alias?>(null) }
    // Filtering runs once per change of inputs, not on every recomposition (and not twice).
    val visible = remember(state.aliases, state.filter, state.query) { state.visible }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            val result = snackbar.showSnackbar(
                message = msg.text,
                actionLabel = if (msg.copyText != null) copy else null,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed && msg.copyText != null) {
                copyToClipboard(context, msg.copyText)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_button))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.providers))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_alias))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // The keyboard must not cover the note being edited on a card.
        Column(Modifier.padding(padding).consumeWindowInsets(padding).imePadding()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                singleLine = true,
                shape = RoundedCornerShape(48.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                placeholder = { Text(stringResource(R.string.searchbar_text)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.filter == null,
                        onClick = { vm.setFilter(null) },
                        label = { Text(stringResource(R.string.section_all)) },
                    )
                }
                items(state.configured) { type ->
                    FilterChip(
                        selected = state.filter == type,
                        onClick = { vm.setFilter(type) },
                        label = { Text(type.label) },
                    )
                }
            }
            state.errors.values.forEach { ErrorBanner(it) }

            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = { vm.refresh() },
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    // Bottom padding keeps the last card clear of the floating button.
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (visible.isEmpty() && !state.loading) {
                        item {
                            Text(
                                text = if (state.aliases.isEmpty()) stringResource(R.string.empty_vault_text)
                                else stringResource(R.string.empty_search_result),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    items(visible, key = { it.key }) { alias ->
                        AliasCard(
                            alias = alias,
                            busy = alias.key in state.busy,
                            onToggle = { vm.toggle(alias) },
                            onCopy = { copyToClipboard(context, alias.address) },
                            onSaveNote = { vm.updateNote(alias, it) },
                            onDelete = { deleteFor = alias },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateAliasSheet(
            providers = state.configured,
            initial = state.filter,
            loadOptions = { type -> vm.createOptions(type) },
            onDismiss = { showCreate = false },
            onCreate = { type, spec ->
                showCreate = false
                vm.create(type, spec)
            },
        )
    }
    deleteFor?.let { alias ->
        ConfirmDeleteDialog(
            alias = alias,
            onDismiss = { deleteFor = null },
            onConfirm = {
                deleteFor = null
                vm.delete(alias)
            },
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun AliasCard(
    alias: Alias,
    busy: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onSaveNote: (String) -> Unit,
    onDelete: () -> Unit,
) {
    // In-place note editing: the Edit button turns into a filled ✔ while the note is editable.
    var editing by remember(alias.key) { mutableStateOf(false) }
    var draft by remember(alias.key) { mutableStateOf(TextFieldValue(alias.note)) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Follow the stored note whenever we're not editing (also rolls back a failed save).
    LaunchedEffect(alias.note, busy, editing) {
        if (!editing && !busy) draft = TextFieldValue(alias.note)
    }
    LaunchedEffect(editing) {
        if (editing) {
            draft = draft.copy(selection = TextRange(draft.text.length))
            focus.requestFocus()
        }
    }
    val commit = {
        val text = draft.text.trim()
        editing = false
        focusManager.clearFocus()
        if (text != alias.note) onSaveNote(text)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (alias.enabled) 1f else 0.6f),
    ) {
        Column {
            // Address + on/off switch
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    alias.address,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = alias.enabled, onCheckedChange = { onToggle() }, enabled = !busy)
            }

            if (editing || alias.note.isNotBlank()) {
                NoteField(
                    value = draft,
                    onValueChange = { draft = it },
                    editing = editing,
                    onDone = commit,
                    focusRequester = focus,
                )
            }

            // Provider and counters; wraps onto a second line on narrow screens.
            FlowRow(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Stat(icon = when (alias.provider) {
                    ProviderType.SIMPLELOGIN -> painterResource(R.drawable.ic_sl)
                    ProviderType.ADDY -> painterResource(R.drawable.ic_addy)
                    ProviderType.RELAY -> painterResource(R.drawable.ic_relay)
                }, text = alias.provider.label
                )
                Stat(
                    painterResource(R.drawable.ic_block),
                    stringResource(R.string.alias_blocked, alias.blocked),
                )
                Stat(
                    rememberVectorPainter(Icons.Default.Email),
                    stringResource(R.string.alias_forwarded, alias.forwarded),
                )
            }

            // Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(top = 8.dp, bottom = 8.dp),
            ) {
                CardAction(
                    icon = painterResource(R.drawable.ic_copy),
                    label = stringResource(R.string.copy),
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                )
                VerticalDivider(Modifier.padding(vertical = 10.dp))
                CardAction(
                    icon = rememberVectorPainter(Icons.Default.Edit),
                    label = stringResource(R.string.edit_note),
                    onClick = { if (editing) commit() else editing = true },
                    enabled = !busy,
                    active = editing,
                    dimWhenDisabled = false,
                    activeIcon = rememberVectorPainter(Icons.Default.Check),
                    activeLabel = stringResource(R.string.save_button),
                    modifier = Modifier.weight(1f),
                )
                VerticalDivider(Modifier.padding(vertical = 10.dp))
                CardAction(
                    icon = rememberVectorPainter(Icons.Default.Delete),
                    label = stringResource(R.string.delete_button),
                    onClick = onDelete,
                    enabled = !busy,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Small icon + text pair used for the provider name and the blocked/forwarded counters. */
@Composable
private fun Stat(icon: Painter, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Borderless note text: reads like a muted subtitle, and gains a soft background while editable. */
@Composable
private fun NoteField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    editing: Boolean,
    onDone: () -> Unit,
    focusRequester: FocusRequester,
) {
    val background by animateColorAsState(
        if (editing) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
        label = "note-bg",
    )
    // Outer 8dp + inner 8dp keeps the text aligned with the address (16dp) in both states.
    Box(
        modifier = Modifier
            .padding(start = 8.dp, end = 8.dp, top = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = editing,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = if (editing) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            maxLines = if (editing) 6 else 2,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                if (value.text.isEmpty()) {
                    Text(
                        stringResource(R.string.note_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                inner()
            },
        )
    }
}

/**
 * One button of the bottom action row. When [active] it becomes a filled button showing
 * [activeIcon]/[activeLabel] instead (used for the Edit → Save ✔ switch).
 */
@Composable
private fun CardAction(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    active: Boolean = false,
    activeIcon: Painter = icon,
    activeLabel: String = label,
    dimWhenDisabled: Boolean = true,
) {
    val bg by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(200),
        label = "action-bg",
    )
    val fg by animateColorAsState(
        if (active) MaterialTheme.colorScheme.onPrimary else tint,
        animationSpec = tween(200),
        label = "action-fg",
    )
    Box(
        modifier = modifier
            .padding(horizontal = 6.dp)
            .alpha(if (enabled || !dimWhenDisabled) 1f else 0.38f)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = if (active) activeIcon else icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = fg,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (active) activeLabel else label,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
