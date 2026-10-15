package com.dawidggs.aliashub.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dawidggs.aliashub.AliasViewModel
import com.dawidggs.aliashub.R

@Composable
fun AliasHubApp(vm: AliasViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val needsSetup = state.configured.isEmpty()
    val close = { showSettings = false }

    BackHandler(enabled = showSettings && !needsSetup) { close() }

    // The credential store loads off the main thread; don't flash the setup screen meanwhile.
    if (!state.initialized) return

    if (needsSetup || showSettings) {
        // First run has nothing to go back to, so no close button until a provider exists.
        SettingsScreen(state = state, vm = vm, onClose = if (needsSetup) null else close)
    } else {
        AliasListScreen(state = state, vm = vm, onOpenSettings = { showSettings = true })
    }
}

fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.alias), text))
    // Android 13+ shows its own "copied" confirmation.
    if (Build.VERSION.SDK_INT < 33) {
        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }
}
