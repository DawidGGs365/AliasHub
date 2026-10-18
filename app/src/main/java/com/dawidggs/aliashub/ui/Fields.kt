package com.dawidggs.aliashub.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dawidggs.aliashub.R

/** Filled text-field colors with one container color for focused and unfocused state alike. */
@Composable
internal fun filledFieldColors(container: Color = MaterialTheme.colorScheme.surfaceContainerHighest) =
    TextFieldDefaults.colors(focusedContainerColor = container, unfocusedContainerColor = container)

/** Rounded filled field shared by the dialogs, sheets and setup forms. */
@Composable
internal fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        colors = filledFieldColors(container),
        shape = RoundedCornerShape(16.dp),
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        modifier = modifier,
    )
}

/** The masked API key field (setup page and Settings "connect" card). */
@Composable
internal fun ApiKeyField(value: String, onValueChange: (String) -> Unit, container: Color) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        container = container,
        label = { Text(stringResource(R.string.api_key)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
}

/** The optional self-hosted instance URL field. */
@Composable
internal fun BaseUrlField(value: String, onValueChange: (String) -> Unit, container: Color) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        container = container,
        label = { Text(stringResource(R.string.self_hosted_url)) },
        placeholder = { Text("https://…") },
        singleLine = true,
    )
}
