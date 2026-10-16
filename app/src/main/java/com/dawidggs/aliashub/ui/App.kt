package com.dawidggs.aliashub.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
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

    val screen = when {
        needsSetup -> Screen.SETUP
        showSettings -> Screen.SETTINGS
        else -> Screen.LIST
    }

    // Forward (deeper) screens slide in from the right over the old one, which drifts away a
    // little slower (parallax); going back reverses it with the old screen sliding off on top.
    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val forward = targetState.depth > initialState.depth
            val spec = tween<IntOffset>(durationMillis = 350, easing = FastOutSlowInEasing)
            ContentTransform(
                targetContentEnter = slideInHorizontally(spec) { w -> if (forward) w else -w / 4 },
                initialContentExit = slideOutHorizontally(spec) { w -> if (forward) -w / 4 else w },
                targetContentZIndex = if (forward) 1f else -1f,
            )
        },
        label = "screen",
    ) { target ->
        // Opaque, so the screen underneath never shows through mid-slide.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (target) {
                // No provider yet: welcome + "add your first provider". If the user just removed
                // their last provider from Settings, skip the welcome page.
                Screen.SETUP -> SetupScreen(vm = vm, startPage = if (showSettings) 1 else 0)
                Screen.SETTINGS -> SettingsScreen(state = state, vm = vm, onClose = close)
                Screen.LIST -> AliasListScreen(
                    state = state,
                    vm = vm,
                    onOpenSettings = { showSettings = true },
                )
            }
        }
    }
}

/** Screens ordered by how deep they sit in the flow; decides which way a transition slides. */
private enum class Screen(val depth: Int) { SETUP(0), LIST(1), SETTINGS(2) }



fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.alias), text))
    // Android 13+ shows its own "copied" confirmation.
    if (Build.VERSION.SDK_INT < 33) {
        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }
}
