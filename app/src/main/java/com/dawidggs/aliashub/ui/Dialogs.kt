package com.dawidggs.aliashub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dawidggs.aliashub.R
import com.dawidggs.aliashub.data.Alias
import com.dawidggs.aliashub.data.ProviderType

/** Filled button in the error colors: the confirmation action of anything destructive. */
@Composable
private fun DestructiveButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) { Text(text) }
}

/**
 * Deleting an alias can't be undone, so the Delete button stays locked until the alias address
 * has been typed out (case and surrounding spaces don't matter). Addy.io gets an extra note,
 * because its delete only deactivates the alias and "Forget" has to be done on the website.
 */
@Composable
fun ConfirmDeleteDialog(alias: Alias, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember(alias.key) { mutableStateOf("") }
    val unlocked = typed.trim().equals(alias.address, ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(32.dp)) },
        iconContentColor = MaterialTheme.colorScheme.error,
        title = { Text(stringResource(R.string.delete_alias_confirmation)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.delete_alias_warning_text, alias.address))
                if (alias.provider == ProviderType.ADDY) {
                    NoteBox(stringResource(R.string.addy_delete_note))
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.delete_type_label)) },
                    supportingText = { Text(stringResource(R.string.delete_type_hint, alias.address)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (unlocked) onConfirm() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            DestructiveButton(stringResource(R.string.delete_button), enabled = unlocked, onClick = onConfirm)
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.cancel_button))
            }
        },
    )
}

/** Disconnecting a provider only forgets its key on this device; nothing is deleted on the service. */
@Composable
fun ConfirmRemoveProviderDialog(type: ProviderType, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(32.dp)) },
        iconContentColor = MaterialTheme.colorScheme.error,
        title = { Text(stringResource(R.string.remove_provider_title, type.label)) },
        text = { Text(stringResource(R.string.remove_provider_text, type.label)) },
        confirmButton = { DestructiveButton(stringResource(R.string.remove_button), onClick = onConfirm) },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.cancel_button))
            }
        },
    )
}

/** A tonal callout inside a dialog: an info icon next to a short explanation. */
@Composable
private fun NoteBox(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}
