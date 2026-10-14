@file:OptIn(ExperimentalMaterial3Api::class)

package com.dawidggs.aliashub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dawidggs.aliashub.AliasViewModel
import com.dawidggs.aliashub.UiState
import com.dawidggs.aliashub.data.Alias
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun AliasListScreen(state: UiState, vm: AliasViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }
    var noteFor by remember { mutableStateOf<Alias?>(null) }
    var deleteFor by remember { mutableStateOf<Alias?>(null) }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            val result = snackbar.showSnackbar(
                message = msg.text,
                actionLabel = if (msg.copyText != null) "Copy" else null,
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
                title = { Text("AliasHub") },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Providers")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = "New alias")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                singleLine = true,
                placeholder = { Text("Search addresses and notes") },
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
                        label = { Text("All") },
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
                    if (state.visible.isEmpty() && !state.loading) {
                        item {
                            Text(
                                text = if (state.aliases.isEmpty()) "No aliases yet. Tap + to create one."
                                else "Nothing matches your search.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    items(state.visible, key = { it.key }) { alias ->
                        AliasCard(
                            alias = alias,
                            busy = alias.key in state.busy,
                            onToggle = { vm.toggle(alias) },
                            onCopy = { copyToClipboard(context, alias.address) },
                            onEditNote = { noteFor = alias },
                            onDelete = { deleteFor = alias },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateAliasDialog(
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
    noteFor?.let { alias ->
        NoteDialog(
            alias = alias,
            onDismiss = { noteFor = null },
            onSave = { note ->
                noteFor = null
                vm.updateNote(alias, note)
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
    onEditNote: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onCopy,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (alias.enabled) 1f else 0.6f),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    alias.address,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (alias.note.isNotBlank()) {
                    Text(
                        alias.note,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${alias.provider.label} · ${alias.forwarded} forwarded · ${alias.blocked} blocked",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = alias.enabled, onCheckedChange = { onToggle() }, enabled = !busy)
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp,
                    shadowElevation = 8.dp,) {
                    DropdownMenuItem(
                        text = { Text("Copy address") },
                        onClick = { menu = false; onCopy() },
                    )
                    DropdownMenuItem(
                        text = { Text("Edit note") },
                        onClick = { menu = false; onEditNote() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}
