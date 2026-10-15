@file:OptIn(ExperimentalMaterial3Api::class)

package com.dawidggs.aliashub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dawidggs.aliashub.AliasViewModel
import com.dawidggs.aliashub.UiState
import com.dawidggs.aliashub.data.ProviderType
import com.dawidggs.aliashub.R

@Composable
fun SettingsScreen(state: UiState, vm: AliasViewModel, onClose: (() -> Unit)?) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (onClose == null) stringResource(R.string.connect_provider) else stringResource(R.string.providers)) },
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
                    ConnectCard(type, onConnect = { token, baseUrl -> vm.saveConfig(type, token, baseUrl) })
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
private fun ConnectCard(type: ProviderType, onConnect: (token: String, baseUrl: String) -> String?) {
    // Plain remember (not rememberSaveable) so the secret doesn't end up in saved instance state.
    var token by remember(type) { mutableStateOf("") }
    var baseUrl by remember(type) { mutableStateOf("") }
    var error by remember(type) { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(type.label, style = MaterialTheme.typography.titleMedium)
            Text(keyHint(type), style = MaterialTheme.typography.bodySmall)

            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(stringResource(R.string.api_key)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            if (type != ProviderType.RELAY) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
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
            Button(onClick = {
                error = onConnect(token, baseUrl)
                if (error == null) token = ""
            }) { Text(stringResource(R.string.connect_button)) }
        }
    }
}

@Composable
private fun keyHint(type: ProviderType): String = when (type) {
    ProviderType.SIMPLELOGIN ->
        stringResource(R.string.api_key_creation_instruction_sl)
    ProviderType.ADDY ->
        stringResource(R.string.api_key_creation_instruction_addy)
    ProviderType.RELAY ->
        stringResource(R.string.api_key_creation_instruction_relay)
}
