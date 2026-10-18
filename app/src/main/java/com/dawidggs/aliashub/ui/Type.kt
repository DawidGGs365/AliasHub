package com.dawidggs.aliashub.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * The font the device itself uses for UI text (`Typeface.DEFAULT`), which is what follows the
 * user's system font setting, including OEM fonts. Compose's own default family doesn't.
 *
 * A Compose FontFamily built straight from a Typeface ignores weights, so every weight is
 * registered as its own font instead; titles stay medium and `FontWeight.Bold` still renders bold.
 */
internal val SystemFontFamily: FontFamily by lazy {
    FontFamily(
        listOf(
            FontWeight.Thin, FontWeight.ExtraLight, FontWeight.Light, FontWeight.Normal,
            FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold,
            FontWeight.Black,
        ).map { SystemFont(it) },
    )
}

private class SystemFont(override val weight: FontWeight) : AndroidFont(
    FontLoadingStrategy.Blocking,
    SystemTypefaceLoader,
    FontVariation.Settings(),
) {
    override val style: FontStyle = FontStyle.Normal
}

private object SystemTypefaceLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface =
        create(font.weight)

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface =
        create(font.weight)

    private fun create(weight: FontWeight): Typeface =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.DEFAULT, weight.weight, false)
        } else {
            // Before API 28 only regular and bold exist.
            Typeface.create(Typeface.DEFAULT, if (weight.weight >= 600) Typeface.BOLD else Typeface.NORMAL)
        }
}

/** [this] with every text style switched to the system font. Sizes, weights and spacing are kept. */
internal fun Typography.withSystemFont(): Typography {
    fun TextStyle.sys() = copy(fontFamily = SystemFontFamily)
    return copy(
        displayLarge = displayLarge.sys(),
        displayMedium = displayMedium.sys(),
        displaySmall = displaySmall.sys(),
        headlineLarge = headlineLarge.sys(),
        headlineMedium = headlineMedium.sys(),
        headlineSmall = headlineSmall.sys(),
        titleLarge = titleLarge.sys(),
        titleMedium = titleMedium.sys(),
        titleSmall = titleSmall.sys(),
        bodyLarge = bodyLarge.sys(),
        bodyMedium = bodyMedium.sys(),
        bodySmall = bodySmall.sys(),
        labelLarge = labelLarge.sys(),
        labelMedium = labelMedium.sys(),
        labelSmall = labelSmall.sys(),
        displayLargeEmphasized = displayLargeEmphasized.sys(),
        displayMediumEmphasized = displayMediumEmphasized.sys(),
        displaySmallEmphasized = displaySmallEmphasized.sys(),
        headlineLargeEmphasized = headlineLargeEmphasized.sys(),
        headlineMediumEmphasized = headlineMediumEmphasized.sys(),
        headlineSmallEmphasized = headlineSmallEmphasized.sys(),
        titleLargeEmphasized = titleLargeEmphasized.sys(),
        titleMediumEmphasized = titleMediumEmphasized.sys(),
        titleSmallEmphasized = titleSmallEmphasized.sys(),
        bodyLargeEmphasized = bodyLargeEmphasized.sys(),
        bodyMediumEmphasized = bodyMediumEmphasized.sys(),
        bodySmallEmphasized = bodySmallEmphasized.sys(),
        labelLargeEmphasized = labelLargeEmphasized.sys(),
        labelMediumEmphasized = labelMediumEmphasized.sys(),
        labelSmallEmphasized = labelSmallEmphasized.sys(),
    )
}
