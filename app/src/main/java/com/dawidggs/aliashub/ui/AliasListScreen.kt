@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.dawidggs.aliashub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dawidggs.aliashub.AliasViewModel
import com.dawidggs.aliashub.R
import com.dawidggs.aliashub.UiState
import com.dawidggs.aliashub.data.Alias
import com.dawidggs.aliashub.data.ProviderType
import kotlinx.coroutines.launch

@Composable
fun AliasListScreen(state: UiState, vm: AliasViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val copy = stringResource(R.string.copy)
    // The provider the "new alias" sheet is open for; picked from the FAB menu.
    var createFor by remember { mutableStateOf<ProviderType?>(null) }
    var deleteFor by remember { mutableStateOf<Alias?>(null) }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    // Filtering runs once per change of inputs, not on every recomposition (and not twice).
    val visible = remember(state.aliases, state.filter, state.query) { state.visible }

    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState(state.query)
    val searchScrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
    val pullState = rememberPullToRefreshState()

    // The view model owns the query (it does the filtering); mirror the text field into it.
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect { vm.setQuery(it) }
    }

    BackHandler(enabled = fabExpanded) { fabExpanded = false }

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

    val newAliasLabel = stringResource(R.string.new_alias)
    val backLabel = stringResource(R.string.back_button)
    val clearLabel = stringResource(R.string.clear_button)
    val searchHint = stringResource(R.string.searchbar_text)

    // Shared by the collapsed app bar and the expanded full-screen search.
    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
            lineLimits = TextFieldLineLimits.SingleLine,
            placeholder = { Text(searchHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = {
                if (searchBarState.currentValue == SearchBarValue.Expanded) {
                    IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel)
                    }
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = { textFieldState.clearText() }) {
                        Icon(Icons.Default.Close, contentDescription = clearLabel)
                    }
                }
            },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(searchScrollBehavior.nestedScrollConnection),
        topBar = {
            AppBarWithSearch(
                state = searchBarState,
                inputField = inputField,
                scrollBehavior = searchScrollBehavior,
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
            // Pick the provider right here, so the sheet only asks for domain and note.
            FloatingActionButtonMenu(
                expanded = fabExpanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = fabExpanded,
                        onCheckedChange = { fabExpanded = it },
                        modifier = Modifier
                            .semantics { contentDescription = newAliasLabel }
                            .animateFloatingActionButton(visible = true, alignment = Alignment.BottomEnd),
                    ) {
                        val icon by remember {
                            derivedStateOf { if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add }
                        }
                        Icon(
                            painter = rememberVectorPainter(icon),
                            contentDescription = null,
                            modifier = Modifier.animateIcon({ checkedProgress }),
                        )
                    }
                },
            ) {
                state.configured.forEach { type ->
                    FloatingActionButtonMenuItem(
                        onClick = {
                            fabExpanded = false
                            createFor = type
                        },
                        icon = { Icon(providerIcon(type), contentDescription = null, modifier = Modifier.size(36.dp)) },
                        text = { Text(type.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            // The keyboard must not cover the note being edited on a card.
            Column(Modifier.padding(padding).consumeWindowInsets(padding).imePadding()) {
                ProviderFilter(
                    selected = state.filter,
                    providers = state.configured,
                    onSelect = vm::setFilter,
                )
                state.errors.values.forEach { ErrorBanner(it) }

                PullToRefreshBox(
                    isRefreshing = state.loading,
                    onRefresh = { vm.refresh() },
                    state = pullState,
                    modifier = Modifier.weight(1f),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullState,
                            isRefreshing = state.loading,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    },
                ) {
                    AliasList(
                        state = state,
                        visible = visible,
                        vm = vm,
                        onDelete = { deleteFor = it },
                        // Bottom padding keeps the last card clear of the floating button.
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Dims the list while the FAB menu is open; tapping it closes the menu.
            AnimatedVisibility(visible = fabExpanded, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { fabExpanded = false },
                )
            }
        }
    }

    // Full-screen search: the results are the same grouped alias list, filtered live.
    ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
        AliasList(
            state = state,
            visible = visible,
            vm = vm,
            onDelete = { deleteFor = it },
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxSize().imePadding(),
            grouped = false,
        )
    }

    createFor?.let { type ->
        CreateAliasSheet(
            provider = type,
            loadOptions = { vm.createOptions(it) },
            onDismiss = { createFor = null },
            onCreate = { provider, spec ->
                createFor = null
                vm.create(provider, spec)
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

/**
 * Segmented items default to the plain surface color, which is the screen background (and pure
 * black in OLED mode), so the cards would vanish. Lift them one tonal step.
 */
@Composable
internal fun groupedItemColors() =
    ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)

/** Provider logo used on cards, in the FAB menu and in Settings. */
@Composable
internal fun providerIcon(type: ProviderType): Painter = when (type) {
    ProviderType.SIMPLELOGIN -> painterResource(R.drawable.ic_sl)
    ProviderType.ADDY -> painterResource(R.drawable.ic_addy)
    ProviderType.RELAY -> painterResource(R.drawable.ic_relay)
}

/**
 * Single-select connected button group: "All" plus one button per connected provider. The outer
 * corners are round, the inner ones small, and the selected button turns into a pill.
 */
@Composable
private fun ProviderFilter(
    selected: ProviderType?,
    providers: List<ProviderType>,
    onSelect: (ProviderType?) -> Unit,
) {
    val options: List<ProviderType?> = listOf(null) + providers
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = selected == option,
                // Single select: tapping the selected button again keeps it selected.
                onCheckedChange = { if (it) onSelect(option) },
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(option?.label ?: stringResource(R.string.section_all), maxLines = 1)
            }
        }
    }
}

/** The grouped alias list, used both under the app bar and inside the expanded search. */
@Composable
private fun AliasList(
    state: UiState,
    visible: List<Alias>,
    vm: AliasViewModel,
    onDelete: (Alias) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** False = one plain group with no provider headers (used for anything search related). */
    grouped: Boolean = true,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
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
        // While searching or filtering to one provider, results are one plain segmented group with no
        // provider headers (a header would just repeat the selected filter).
        // Otherwise: one group per provider under its own header; empty groups are skipped.
        val groups: List<Pair<ProviderType?, List<Alias>>> =
            if (!grouped || state.query.isNotBlank() || state.filter != null) listOf(null to visible)
            else ProviderType.entries.map { it to visible.filter { a -> a.provider == it } }

        groups.forEach { (type, group) ->
            if (group.isEmpty()) return@forEach
            if (type != null) {
                item(key = "header-$type") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .animateItem()
                            .padding(start = 16.dp, top = 12.dp, bottom = 6.dp),
                    ) {
                        Icon(
                            painter = providerIcon(type),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            type.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            itemsIndexed(group, key = { _, alias -> alias.key }) { index, alias ->
                AliasCard(
                    alias = alias,
                    index = index,
                    count = group.size,
                    busy = alias.key in state.busy,
                    onToggle = { vm.toggle(alias) },
                    onCopy = { copyToClipboard(context, alias.address) },
                    onSaveNote = { vm.updateNote(alias, it) },
                    onDelete = { onDelete(alias) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
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
    index: Int,
    count: Int,
    busy: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onSaveNote: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
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

    // Non-interactive segmented item: first/last cards get the large outer corners, the ones in
    // between small inner corners. All the content lives in one full-width column (no trailing
    // slot, which would narrow everything below the address) so the action buttons span the card.
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = groupedItemColors(),
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (alias.enabled) 1f else 0.6f),
        contentPadding = PaddingValues(0.dp),
        content = {
            Column(Modifier.fillMaxWidth()) {
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
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        NoteField(
                            value = draft,
                            onValueChange = { draft = it },
                            editing = editing,
                            onDone = commit,
                            focusRequester = focus,
                        )
                    }
                }

                // Counters; wraps onto a second line on narrow screens.
                FlowRow(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Stat(
                        painterResource(R.drawable.ic_block),
                        stringResource(R.string.alias_blocked, alias.blocked),
                    )
                    Stat(
                        rememberVectorPainter(Icons.Default.Email),
                        stringResource(R.string.alias_forwarded, alias.forwarded),
                    )
                }

                // Actions: three equal buttons that fill the card width.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CardAction(
                        icon = painterResource(R.drawable.ic_copy),
                        label = stringResource(R.string.copy),
                        onClick = onCopy,
                        modifier = Modifier.weight(1f),
                    )
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
        },
    )
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

/** Borderless note text: reads like a muted subtitle, and has no background in either state. */
@Composable
private fun NoteField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    editing: Boolean,
    onDone: () -> Unit,
    focusRequester: FocusRequester,
) {
    Box(
        modifier = Modifier
            .padding(top = 2.dp)
            .fillMaxWidth()
            .padding(vertical = 6.dp),
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
 * One button of the bottom action row: an expressive tonal button (its corners morph while
 * pressed). When [active] it fills with the primary color and shows [activeIcon]/[activeLabel]
 * instead (used for the Edit → Save ✔ switch).
 */
@Composable
private fun CardAction(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    active: Boolean = false,
    activeIcon: Painter = icon,
    activeLabel: String = label,
    dimWhenDisabled: Boolean = true,
) {
    val bg by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else container,
        animationSpec = tween(200),
        label = "action-bg",
    )
    val fg by animateColorAsState(
        if (active) MaterialTheme.colorScheme.onPrimary else tint,
        animationSpec = tween(200),
        label = "action-fg",
    )
    // Resting shape is a rounded square; while [active] the button morphs into a full pill and
    // springs back to the square when it is released. Press morphing still comes from `shapes`.
    val corner by animateDpAsState(
        targetValue = if (active) 28.dp else 12.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "action-corner",
    )
    FilledTonalButton(
        // A busy "Edit" stays fully opaque (not dimmed) but ignores taps.
        onClick = { if (enabled) onClick() },
        enabled = enabled || !dimWhenDisabled,
        shapes = ButtonDefaults.shapes(
            shape = RoundedCornerShape(corner.coerceAtLeast(0.dp)),
            pressedShape = ButtonDefaults.pressedShape,
        ),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = bg, contentColor = fg),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier,
    ) {
        Icon(
            painter = if (active) activeIcon else icon,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(
            if (active) activeLabel else label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
