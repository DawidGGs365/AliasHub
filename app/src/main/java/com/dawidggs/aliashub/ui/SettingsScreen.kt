@file:OptIn(ExperimentalMaterial3Api::class)

package com.dawidggs.aliashub.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dawidggs.aliashub.AliasViewModel
import com.dawidggs.aliashub.UiState
import com.dawidggs.aliashub.data.ProviderType
import com.dawidggs.aliashub.data.ThemeMode
import com.dawidggs.aliashub.R

@Composable
fun SettingsScreen(state: UiState, vm: AliasViewModel, onClose: (() -> Unit)?) {
    Scaffold(
        topBar = {
            TopAppBar(
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
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(ProviderType.entries.toList()) { type ->
                if (type in state.configured) {
                    ConnectedCard(type, onRemove = { vm.removeProvider(type) })
                } else {
                    ConnectCard(type, onConnect = { token, baseUrl ->
                        vm.saveConfig(type, token, baseUrl)
                    })
                }
            }
            if (onClose != null) {
                item {
                    ThemeCard(selected = state.themeMode, onSelect = vm::setThemeMode)
                }
            }
        }
    }
}

/** A connected provider is just its name and a way to disconnect it. */
@Composable
private fun ConnectedCard(type: ProviderType, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = when(type) {
                    ProviderType.SIMPLELOGIN -> painterResource(R.drawable.ic_sl)
                    ProviderType.ADDY -> painterResource(R.drawable.ic_addy)
                    ProviderType.RELAY -> painterResource(R.drawable.ic_relay)
                },
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
            Spacer(
                modifier = Modifier.width(10.dp)
            )
            Text(
                stringResource(R.string.provider_connected, type.label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onRemove) { Text(stringResource(R.string.remove_button)) }
        }
    }
}

@Composable
private fun ConnectCard(type: ProviderType, onConnect: suspend (token: String, baseUrl: String) -> String?) {
    // Plain remember (not rememberSaveable) so the secret doesn't end up in saved instance state.
    var token by remember(type) { mutableStateOf("") }
    var baseUrl by remember(type) { mutableStateOf("") }
    var error by remember(type) { mutableStateOf<String?>(null) }
    var checking by remember(type) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()


    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(type.label, style = MaterialTheme.typography.titleMedium)
            Text(keyHint(type), style = MaterialTheme.typography.bodySmall)

            TextField(
                value = token,
                onValueChange = { token = it },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
    }
}

@Composable
private fun ThemeCard(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    val selectedLabel = stringResource(
        when (selected) {
            ThemeMode.SYSTEM -> R.string.theme_system
            ThemeMode.LIGHT -> R.string.theme_light
            ThemeMode.DARK -> R.string.theme_dark
            ThemeMode.OLED -> R.string.theme_oled
        },
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = when(selected) {
                ThemeMode.SYSTEM -> painterResource(R.drawable.ic_theme_auto)
                ThemeMode.LIGHT -> painterResource(R.drawable.ic_theme_light)
                ThemeMode.DARK -> painterResource(R.drawable.ic_theme_dark)
                ThemeMode.OLED -> painterResource(R.drawable.ic_theme_dark)
                },
                contentDescription = null
            )
            Spacer(
                modifier = Modifier.width(10.dp)
            )
            Text(
                stringResource(R.string.theme),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )

            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(selectedLabel)
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    shape = RoundedCornerShape(20.dp),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        when (mode) {
                                            ThemeMode.SYSTEM -> R.string.theme_system
                                            ThemeMode.LIGHT -> R.string.theme_light
                                            ThemeMode.DARK -> R.string.theme_dark
                                            ThemeMode.OLED -> R.string.theme_oled
                                        }
                                    )
                                )
                            },
                            onClick = {
                                onSelect(mode)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
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
