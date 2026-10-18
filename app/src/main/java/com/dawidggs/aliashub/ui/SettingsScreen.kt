@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.dawidggs.aliashub.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dawidggs.aliashub.AliasViewModel
import com.dawidggs.aliashub.R
import com.dawidggs.aliashub.UiState
import com.dawidggs.aliashub.data.AccountField
import com.dawidggs.aliashub.data.AccountInfo
import com.dawidggs.aliashub.data.AccountItem
import com.dawidggs.aliashub.data.AccountPlan
import com.dawidggs.aliashub.data.PlanFeature
import com.dawidggs.aliashub.data.ProviderType
import com.dawidggs.aliashub.data.ThemeMode
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(state: UiState, vm: AliasViewModel, onClose: (() -> Unit)?) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // Plan and usage can change on the provider's side, so reload them each time Settings opens.
    LaunchedEffect(Unit) { vm.refreshAccounts() }
    var removeFor by remember { mutableStateOf<ProviderType?>(null) }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    if (onClose != null) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(
                                R.string.back_button
                            ))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val providers = ProviderType.entries
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            // One group for the providers (connected or not), one for the theme.
            itemsIndexed(providers) { index, type ->
                val shapes = ListItemDefaults.segmentedShapes(index = index, count = providers.size)
                if (type in state.configured) {
                    ConnectedItem(
                        type, shapes, state.accounts[type],
                        onManage = { vm.dashboardUrl(type) },
                        onRemove = { removeFor = type },
                    )
                } else {
                    ConnectItem(type, shapes, onConnect = { token, baseUrl ->
                        vm.saveConfig(type, token, baseUrl)
                    })
                }
            }
            if (onClose != null) {
                // Appearance group: theme and font sit together as one segmented list.
                item {
                    ThemeItem(
                        selected = state.themeMode,
                        onSelect = vm::setThemeMode,
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                item {
                    FontItem(
                        systemFont = state.systemFont,
                        onChange = vm::setSystemFont,
                        shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                    )
                }
            }
        }
    }
    removeFor?.let { type ->
        ConfirmRemoveProviderDialog(
            type = type,
            onDismiss = { removeFor = null },
            onConfirm = {
                removeFor = null
                vm.removeProvider(type)
            },
        )
    }
}

/**
 * A connected provider: name, then the account info and the two actions, all at the card's full
 * width (the list item's leading/trailing slots would squeeze the details, so they aren't used).
 */
@Composable
private fun ConnectedItem(
    type: ProviderType,
    shapes: ListItemShapes,
    account: AccountInfo?,
    onManage: () -> String,
    onRemove: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    SegmentedListItem(
        shapes = shapes,
        colors = groupedItemColors(),
        contentPadding = PaddingValues(16.dp),
        supportingContent = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (account != null) AccountDetails(account)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = {
                            // No browser installed is rare, but must not crash the app.
                            runCatching { uriHandler.openUri(onManage()) }
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.manage_account_button))
                    }
                    Button(
                        onClick = onRemove,
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.remove_button))
                    }
                }
            }
        },
        content = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = providerIcon(type),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.provider_connected, type.label),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
    )
}

/** Plan line, then one row per fact the provider reported; nothing is shown for what it didn't. */
@Composable
private fun AccountDetails(info: AccountInfo) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        info.plan?.let { plan ->
            Text(
                planText(plan),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        info.items.forEach { item ->
            AccountRow(
                label = stringResource(fieldLabel(item.field)),
                value = itemValue(context, item),
                progress = itemProgress(item),
            )
        }
    }
}

@Composable
private fun AccountRow(label: String, value: String, progress: Float?) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(12.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun planText(plan: AccountPlan): String {
    val date = { millis: Long -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis)) }
    val name = plan.name?.replaceFirstChar { it.uppercase() }
        ?: stringResource(if (plan.paid) R.string.plan_premium else R.string.plan_free)
    val parts = mutableListOf<String>()
    parts += if (plan.trial) stringResource(R.string.plan_trial, name) else name
    plan.features.forEach { parts += stringResource(featureLabel(it)) }
    plan.endsAtMillis?.let {
        parts += stringResource(if (plan.trial) R.string.plan_trial_ends else R.string.plan_ends, date(it))
    }
    plan.sinceMillis?.let { parts += stringResource(R.string.plan_since, date(it)) }
    return parts.joinToString(" · ")
}

private fun itemValue(context: android.content.Context, item: AccountItem): String {
    fun count(n: Long) = java.text.NumberFormat.getIntegerInstance().format(n)
    fun bytes(n: Long) = Formatter.formatShortFileSize(context, n)
    return when (item) {
        is AccountItem.Text -> item.value
        is AccountItem.Count -> when {
            item.limit != null -> context.getString(R.string.usage_of, count(item.used), count(item.limit))
            item.unlimited -> context.getString(R.string.usage_unlimited, count(item.used))
            else -> count(item.used)
        }
        is AccountItem.Bytes -> when {
            item.limit != null -> context.getString(R.string.usage_of, bytes(item.used), bytes(item.limit))
            item.unlimited -> context.getString(R.string.usage_unlimited, bytes(item.used))
            else -> bytes(item.used)
        }
    }
}

/** A bar only where there is a known cap to measure against. */
private fun itemProgress(item: AccountItem): Float? {
    val (used, limit) = when (item) {
        is AccountItem.Count -> item.used to item.limit
        is AccountItem.Bytes -> item.used to item.limit
        is AccountItem.Text -> return null
    }
    return limit?.takeIf { it > 0 }?.let { (used.toFloat() / it).coerceIn(0f, 1f) }
}

private fun fieldLabel(field: AccountField): Int = when (field) {
    AccountField.EMAIL -> R.string.account_email
    AccountField.USERNAME -> R.string.account_username
    AccountField.SUBDOMAIN -> R.string.account_subdomain
    AccountField.ALIASES -> R.string.account_aliases
    AccountField.SHARED_ALIASES -> R.string.account_shared_aliases
    AccountField.RECIPIENTS -> R.string.account_recipients
    AccountField.DOMAINS -> R.string.account_domains
    AccountField.USERNAMES -> R.string.account_usernames
    AccountField.BANDWIDTH -> R.string.account_bandwidth
    AccountField.FORWARDED -> R.string.account_forwarded
    AccountField.BLOCKED -> R.string.account_blocked
    AccountField.REPLIED -> R.string.account_replied
}

private fun featureLabel(feature: PlanFeature): Int = when (feature) {
    PlanFeature.PHONE -> R.string.plan_feature_phone
    PlanFeature.VPN -> R.string.plan_feature_vpn
    PlanFeature.BUNDLE -> R.string.plan_feature_bundle
}

@Composable
private fun ConnectItem(
    type: ProviderType,
    shapes: ListItemShapes,
    onConnect: suspend (token: String, baseUrl: String) -> String?,
) {
    // Plain remember (not rememberSaveable) so the secret doesn't end up in saved instance state.
    var token by remember(type) { mutableStateOf("") }
    var baseUrl by remember(type) { mutableStateOf("") }
    var error by remember(type) { mutableStateOf<String?>(null) }
    var checking by remember(type) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    SegmentedListItem(
        shapes = shapes,
        colors = groupedItemColors(),
        contentPadding = PaddingValues(16.dp),
        supportingContent = {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(keyHint(type), style = MaterialTheme.typography.bodySmall)

                TextField(
                    value = token,
                    onValueChange = { token = it },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                    shape = RoundedCornerShape(16.dp),
                    label = { Text(stringResource(R.string.api_key)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type != ProviderType.RELAY) {
                    TextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                        shape = RoundedCornerShape(16.dp),
                        label = { Text(stringResource(R.string.self_hosted_url)) },
                        placeholder = { Text("https://…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        stringResource(R.string.relay_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    enabled = !checking,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        error = null
                        checking = true
                        scope.launch {
                            // Tests the key against the service before it is stored.
                            error = onConnect(token, baseUrl)
                            if (error == null) token = ""
                            checking = false
                        }
                    },
                ) {
                    if (checking) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(if (checking) R.string.checking_button else R.string.connect_button))
                }
            }
        },
        content = { Text(type.label, style = MaterialTheme.typography.titleMedium) },
    )
}

/** The whole row is the tap target; the current choice is the supporting line, menu hangs off the arrow. */
@Composable
private fun ThemeItem(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    @Composable
    fun label(mode: ThemeMode) = stringResource(
        when (mode) {
            ThemeMode.SYSTEM -> R.string.theme_system
            ThemeMode.LIGHT -> R.string.theme_light
            ThemeMode.DARK -> R.string.theme_dark
            ThemeMode.OLED -> R.string.theme_oled
        },
    )

    SegmentedListItem(
        onClick = { expanded = true },
        shapes = shapes,
        colors = groupedItemColors(),
        modifier = modifier,
        leadingContent = {
            Icon(
                painter = when (selected) {
                    ThemeMode.SYSTEM -> painterResource(R.drawable.ic_theme_auto)
                    ThemeMode.LIGHT -> painterResource(R.drawable.ic_theme_light)
                    ThemeMode.DARK, ThemeMode.OLED -> painterResource(R.drawable.ic_theme_dark)
                },
                contentDescription = null,
            )
        },
        supportingContent = { Text(label(selected)) },
        trailingContent = {
            Box {
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    shape = RoundedCornerShape(20.dp),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(label(mode)) },
                            onClick = {
                                onSelect(mode)
                                expanded = false
                            },
                        )
                    }
                }
            }
        },
        content = { Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium) },
    )
}

/** On: the device's system font. Off: Compose's default typography. */
@Composable
private fun FontItem(systemFont: Boolean, onChange: (Boolean) -> Unit, shapes: ListItemShapes) {
    SegmentedListItem(
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_font),
                contentDescription = null
            )
        },
        onClick = { onChange(!systemFont) },
        shapes = shapes,
        colors = groupedItemColors(),
        supportingContent = {
            Text(stringResource(if (systemFont) R.string.font_system_on else R.string.font_system_off))
        },
        trailingContent = { Switch(checked = systemFont, onCheckedChange = null) },
        content = { Text(stringResource(R.string.font_system), style = MaterialTheme.typography.titleMedium) },
    )
}

@Composable
internal fun keyHint(type: ProviderType): String = when (type) {
    ProviderType.SIMPLELOGIN ->
        stringResource(R.string.api_key_creation_instruction_sl)
    ProviderType.ADDY ->
        stringResource(R.string.api_key_creation_instruction_addy)
    ProviderType.RELAY ->
        stringResource(R.string.api_key_creation_instruction_relay)
}
