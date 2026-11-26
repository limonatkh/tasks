package com.imad.planner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.platform.LocalContext

// The phone's part of the shared theme (shared/src/compose/.../Ui.kt): its font files, the bottom sheet and the
// system switch for animations.

/** Rounded happy Arabic for titles and big numbers. */
val DisplayFont = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(R.font.baloo_bold, FontWeight.Bold),
    androidx.compose.ui.text.font.Font(R.font.baloo_extrabold, FontWeight.ExtraBold),
    androidx.compose.ui.text.font.Font(R.font.baloo_bold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(R.font.baloo_bold, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(R.font.baloo_bold, FontWeight.Normal),
)

/** Clear Arabic for everything people read. */
val BodyFont = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(R.font.plex_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(R.font.plex_regular, FontWeight.Light),
    androidx.compose.ui.text.font.Font(R.font.plex_semibold, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(R.font.plex_semibold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(R.font.plex_bold, FontWeight.Bold),
    androidx.compose.ui.text.font.Font(R.font.plex_bold, FontWeight.ExtraBold),
)

/**
 * A card that comes up from the bottom (260 ms, eases in), the page behind dims. Back or the × closes it.
 * Used for every sheet in the app, so they all open and close the same way.
 */
@Composable
fun Sheet(title: String, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val calm = calmMotion()
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val shown = remember { androidx.compose.animation.core.MutableTransitionState(calm) }.apply { targetState = true }
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = close)
                .statusBarsPadding(), contentAlignment = Alignment.BottomCenter) {
                val cap = maxHeight * 0.94f
                androidx.compose.animation.AnimatedVisibility(
                    visibleState = shown,
                    enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 2 } +
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)),
                ) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = cap)
                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                            .navigationBarsPadding().imePadding(),
                    ) {
                        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                        Box(Modifier.fillMaxWidth().padding(top = 4.dp, start = 6.dp, end = 6.dp)) {
                            Box(Modifier.align(Alignment.Center).width(44.dp).heightIn(min = 5.dp, max = 5.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp)))
                            IconButton(onClick = close, modifier = Modifier.align(Alignment.CenterEnd)) { Icon(Icons.Filled.Close, contentDescription = "إغلاق") }
                        }
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleLarge)
                            content()
                        }
                        }
                    }
                }
            }
        }
    }
}

/** True when the phone's "remove animations" setting is on. */
@Composable
fun systemCalmMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) == 0f
    }
}
