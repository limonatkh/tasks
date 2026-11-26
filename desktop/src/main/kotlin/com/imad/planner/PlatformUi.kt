package com.imad.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

// The computer's part of the shared theme (shared/src/compose/.../Ui.kt): the font files (packed from
// android/src/main/res/font), the side panel that takes the place of the phone's bottom sheet, and the animation switch.

/** Rounded happy Arabic for titles and big numbers. */
val DisplayFont = FontFamily(
    Font("baloo_bold.ttf", FontWeight.Bold),
    Font("baloo_extrabold.ttf", FontWeight.ExtraBold),
    Font("baloo_bold.ttf", FontWeight.SemiBold),
    Font("baloo_bold.ttf", FontWeight.Medium),
    Font("baloo_bold.ttf", FontWeight.Normal),
)

/** Clear Arabic for everything people read. */
val BodyFont = FontFamily(
    Font("plex_regular.ttf", FontWeight.Normal),
    Font("plex_regular.ttf", FontWeight.Light),
    Font("plex_semibold.ttf", FontWeight.Medium),
    Font("plex_semibold.ttf", FontWeight.SemiBold),
    Font("plex_bold.ttf", FontWeight.Bold),
    Font("plex_bold.ttf", FontWeight.ExtraBold),
)

/** Windows has no app-wide "remove animations" switch to read here; the app's own «حركة أقل» still applies. */
@Composable
fun systemCalmMotion(): Boolean = false

/**
 * On the computer a sheet is a panel on the left side of the window (the end side in Arabic), the page behind
 * dims a little. Esc, the × or a click outside closes it. Same title and content as the phone's bottom sheet.
 */
@Composable
fun Sheet(title: String, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Popup(onDismissRequest = close, properties = PopupProperties(focusable = true)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(
                Modifier.fillMaxSize().background(Color(0x331C2340))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close)
                    .onKeyEvent { if (it.key == Key.Escape) { close(); true } else false },
            ) {
                Column(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(460.dp)
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 12.dp, end = 12.dp), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = close) { Icon(Icons.Filled.Close, contentDescription = "إغلاق") }
                        }
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleLarge)
                            content()
                        }
                    }
                }
            }
        }
    }
}
