package com.dawidggs.aliashub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dawidggs.aliashub.AliasViewModel
import com.dawidggs.aliashub.R
import com.dawidggs.aliashub.data.ProviderType
import kotlinx.coroutines.launch

/**
 * First-run flow: a welcome page, then "add your first provider". [startPage] = 1 skips the
 * welcome (used when the user removed their last provider from Settings).
 */
@Composable
fun SetupScreen(vm: AliasViewModel, startPage: Int = 0) {
    val pager = rememberPagerState(initialPage = startPage) { PAGE_COUNT }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(0) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(), // status/nav bars and the keyboard
    ) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> WelcomePage(onNext = { scope.launch { pager.animateScrollToPage(1) } })
                else -> ProviderPage(vm)
            }
        }
        PageDots(
            current = pager.currentPage,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 20.dp),
        )
    }
}

private const val PAGE_COUNT = 2

@Composable
private fun WelcomePage(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLogo()
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.setup_welcome),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.setup_welcome_text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier
                .widthIn(min = 220.dp)
                .heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.setup_next_button), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

/** The launcher icon, drawn the way the launcher masks it (center 72dp of the 108dp canvas). */
@Composable
private fun AppLogo() {
    Box(
        modifier = Modifier
            .size(128.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(192.dp), // 128 * 108 / 72
        )
    }
}

@Composable
private fun ProviderPage(vm: AliasViewModel) {
    var type by remember { mutableStateOf(ProviderType.entries.first()) }
    // Plain remember (not rememberSaveable) so the secret never lands in saved instance state.
    var token by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var showUrl by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }

    // Centred when it fits, scrollable when the keyboard or a small screen squeezes it.
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.setup_add_first_provider),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))

            SelectField(
                label = stringResource(R.string.provider),
                selected = type,
                options = ProviderType.entries,
                optionLabel = { it.label },
                onSelect = {
                    type = it
                    error = null
                },
            )

            Text(
                keyHint(type),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            val container = MaterialTheme.colorScheme.surfaceContainerHigh
            ApiKeyField(
                value = token,
                onValueChange = {
                    token = it
                    error = null
                },
                container = container,
            )

            if (type == ProviderType.RELAY) {
                Text(
                    stringResource(R.string.relay_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (showUrl) {
                BaseUrlField(value = baseUrl, onValueChange = { baseUrl = it }, container = container)
            } else {
                TextButton(onClick = { showUrl = true }) {
                    Text(stringResource(R.string.setup_self_hosted_button))
                }
            }

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(
                enabled = !checking,
                onClick = {
                    error = null
                    checking = true
                    scope.launch {
                        // Tests the key first; on success the app leaves this screen by itself.
                        error = vm.saveConfig(type, token, if (type == ProviderType.RELAY) "" else baseUrl)
                        checking = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                if (checking) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (checking) R.string.checking_button else R.string.connect_button),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun PageDots(current: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(PAGE_COUNT) { index ->
            val active = index == current
            Box(
                modifier = Modifier
                    .size(if (active) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
            )
        }
    }
}
