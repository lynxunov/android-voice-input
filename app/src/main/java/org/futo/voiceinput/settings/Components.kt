package org.futo.voiceinput.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.R
import org.futo.voiceinput.theme.Typography

@Composable
fun ScreenTitle(title: String, showBack: Boolean = false, navController: NavHostController = rememberNavController()) {
    val rowModifier = if(showBack) {
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Navigate back") { navController.navigateUp() }
    } else {
        Modifier.fillMaxWidth()
    }
    Row(modifier = rowModifier) {
        Spacer(modifier = Modifier.width(16.dp))

        if(showBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.align(CenterVertically))
            Spacer(modifier = Modifier.width(18.dp))
        }
        Text(title, style = Typography.Heading.Medium, modifier = Modifier
            .align(CenterVertically)
            .padding(0.dp, 16.dp))
    }
}

enum class TipKind(val icon: ImageVector?, val header: Int?, val bgColor: (ColorScheme) -> Color) {
    Info(Icons.Outlined.Info, R.string.tip_kind_tip, { it.surfaceBright }),
    Warning(Icons.Outlined.Warning, R.string.tip_kind_warning, { it.primaryContainer }),
    Error(Icons.Outlined.Warning, R.string.tip_kind_error, { it.errorContainer }),
    None(null, null, { it.surfaceBright }),
    WhatsNew(Icons.Default.Star, R.string.whats_new_title, { it.primaryContainer })
    /*Warning,
    Notice,
    Error,*/
}

@Composable
fun Tip(text: String = "This is an example tip", kind: TipKind, onAction: (() -> Unit)? = null, actionText: Int = R.string.whats_new_read_more, onDismiss: (() -> Unit)? = null, dismissText: Int = R.string.dismiss) {
    val bgColor = kind.bgColor(MaterialTheme.colorScheme)
    val fgColor = contentColorFor(bgColor)

    Surface(
        color = bgColor, modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp), shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if(kind.icon != null && kind.header != null)
                Row(Modifier.align(Alignment.CenterHorizontally).alpha(0.67f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(kind.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(kind.header), style = Typography.SmallMl.copy(fontWeight = FontWeight.Medium))
                }
            Text(
                text,
                style = Typography.Body.RegularMl,
                color = fgColor
            )
            Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if(onAction != null) {
                    Button(onClick = onAction) {
                        Text(stringResource(actionText))
                    }
                }

                if(onDismiss != null) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(dismissText))
                    }
                }
            }
        }
    }
}


@Composable
fun SettingItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    disabled: Boolean = false,
    compact: Boolean = false,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(0.dp, if (compact) 44.dp else 68.dp)
            .clickable(enabled = !disabled, onClick = {
                if (!disabled) {
                    onClick()
                }
            })
            .padding(0.dp, if (compact) 0.dp else 10.dp, 0.dp, if (compact) 0.dp else 10.dp)
    ) {
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .width(48.dp)
                .align(Alignment.CenterVertically)
        ) {
            Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                if (icon != null) {
                    icon()
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
                .align(Alignment.CenterVertically)
                .alpha(
                    if (disabled) {
                        0.5f
                    } else {
                        1.0f
                    }
                )
        ) {
            Column {
                Text(title, style = Typography.Heading.RegularMl)

                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = Typography.SmallMl,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
        Box(modifier = Modifier.align(Alignment.CenterVertically)) {
            content()
        }

        Spacer(modifier = Modifier.width(12.dp))
    }
}

@Composable
fun SettingToggleRaw(
    title: String,
    enabled: Boolean,
    setValue: (Boolean) -> Unit,
    subtitle: String? = null,
    disabled: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    onChanged: ((Boolean) -> Unit)? = null,
) {
    SettingItem(
        title = title,
        subtitle = subtitle,
        onClick = {
            if (!disabled) {
                setValue(!enabled)
                onChanged?.invoke(!enabled)
            }
        },
        icon = icon
    ) {
        Switch(checked = enabled, onCheckedChange = {
            if (!disabled) {
                setValue(!enabled)
                onChanged?.invoke(!enabled)
            }
        }, enabled = !disabled)
    }
}

@Composable
fun SettingToggleDataStoreItem(
    title: String,
    dataStoreItem: DataStoreItem<Boolean>,
    subtitle: String? = null,
    disabledSubtitle: String? = null,
    disabled: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    onChanged: ((Boolean) -> Unit)? = null,
) {
    val (enabled, setValue) = dataStoreItem

    val subtitleValue = if (!enabled && disabledSubtitle != null) {
        disabledSubtitle
    } else {
        subtitle
    }

    SettingToggleRaw(title, enabled, { setValue(it) }, subtitleValue, disabled, icon, onChanged)
}

@Composable
fun SettingToggleDataStore(
    title: String,
    setting: SettingsKey<Boolean>,
    subtitle: String? = null,
    disabledSubtitle: String? = null,
    disabled: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    onChanged: ((Boolean) -> Unit)? = null,
) {
    SettingToggleDataStoreItem(
        title, useDataStore(setting.key, setting.default), subtitle, disabledSubtitle, disabled, icon, onChanged)
}

@Composable
fun<T> SettingRadio(
    title: String,
    options: List<T>,
    optionNames: List<String>,
    setting: SettingsKey<T>,
    onChanged: (() -> Unit)? = null,
    disabled: Boolean = false
) {
    val (value, setValue) = useDataStore(key = setting.key, default = setting.default)

    Text(
        title,
        style = Typography.Heading.RegularMl,
        modifier = Modifier.padding(start = 76.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
    Column {
        options.zip(optionNames).forEach {
            SettingItem(
                title = it.second,
                onClick = {
                    if(!disabled) {
                        setValue(it.first)
                        onChanged?.invoke()
                    }
                },
                disabled = disabled,
                compact = true,
                icon = { RadioButton(selected = value == it.first, onClick = {
                    if(!disabled) {
                        setValue(it.first)
                        onChanged?.invoke()
                    }
                }, enabled = !disabled) }
            ) { }
        }
    }
}

@Composable
fun SettingsRootColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.systemBars))
        content()
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
    }
}


@Composable
fun SettingsRootList(content: @Composable () -> Unit) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.systemBars))
        content()
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
    }
}

@Composable
fun BasicScrollList(content: @Composable () -> Unit) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        content()
    }
}

@Composable
fun SettingsRootListLazy(content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
    ) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.systemBars)) }
        content()
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars)) }
    }
}


enum class NavigationItemStyle {
    HomePrimary,
    HomeSecondary,
    HomeTertiary,
    MiscNoArrow,
    Misc
}

@Composable
fun NavigationItem(title: String, style: NavigationItemStyle, navigate: () -> Unit, icon: Painter? = null, subtitle: String? = null) {
    SettingItem(
        title = title,
        subtitle = subtitle,
        onClick = navigate,
        icon = {
            icon?.let {
                val circleColor = when(style) {
                    NavigationItemStyle.HomePrimary -> MaterialTheme.colorScheme.primaryContainer
                    NavigationItemStyle.HomeSecondary -> MaterialTheme.colorScheme.secondaryContainer
                    NavigationItemStyle.HomeTertiary -> MaterialTheme.colorScheme.tertiaryContainer
                    NavigationItemStyle.MiscNoArrow -> Color.Transparent
                    NavigationItemStyle.Misc -> Color.Transparent
                }

                val iconColor = when(style) {
                    NavigationItemStyle.HomePrimary -> MaterialTheme.colorScheme.onPrimaryContainer
                    NavigationItemStyle.HomeSecondary -> MaterialTheme.colorScheme.onSecondaryContainer
                    NavigationItemStyle.HomeTertiary -> MaterialTheme.colorScheme.onTertiaryContainer
                    NavigationItemStyle.MiscNoArrow -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                    NavigationItemStyle.Misc -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(circleColor, this.size.maxDimension / 2.4f)
                    translate(
                        left = this.size.width / 2.0f - icon.intrinsicSize.width / 2.0f,
                        top = this.size.height / 2.0f - icon.intrinsicSize.height / 2.0f
                    ) {
                        with(icon) {
                            draw(icon.intrinsicSize, colorFilter = ColorFilter.tint(iconColor))
                        }
                    }
                }
            }
        }
    ) {
        when(style) {
            NavigationItemStyle.Misc -> Icon(Icons.Default.ArrowForward, contentDescription = null)
            else -> {}
        }
    }
}

/**
 * Slider with a step grid plus exact keyboard entry (tap the value). The slider covers [sliderMin]..[sliderMax]
 * in [step] units; typed values may use the wider [inputMin]..[inputMax] range at 1-unit precision. An off-grid
 * value stays as it is until the slider is moved, then snaps to the next grid position in that direction.
 */
@Composable
fun StepSliderSetting(
    title: String,
    subtitle: String? = null,
    value: Int,
    onValueChange: (Int) -> Unit,
    unit: String,
    sliderMin: Int,
    sliderMax: Int,
    step: Int,
    inputMin: Int,
    inputMax: Int,
    enabled: Boolean = true,
) {
    var shown by androidx.compose.runtime.remember(value) { androidx.compose.runtime.mutableIntStateOf(value) }
    var showDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 76.dp, end = 28.dp, top = 10.dp, bottom = 10.dp)
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        Row(verticalAlignment = CenterVertically) {
            Text(title, style = Typography.Heading.RegularMl, modifier = Modifier.weight(1f))
            Text(
                "$shown $unit",
                style = Typography.Heading.RegularMl,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(enabled = enabled) { showDialog = true }
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
            )
        }
        if (subtitle != null) {
            Text(subtitle, style = Typography.SmallMl, color = MaterialTheme.colorScheme.outline)
        }
        androidx.compose.material3.Slider(
            value = shown.toFloat().coerceIn(sliderMin.toFloat(), sliderMax.toFloat()),
            onValueChange = { raw ->
                val snapped = (Math.round((raw - sliderMin) / step.toFloat()) * step + sliderMin)
                    .coerceIn(sliderMin, sliderMax)
                // Off-grid start (e.g. 261): the first move goes to the neighbouring grid position (275 / 250).
                shown = if (shown % step != 0 || (shown - sliderMin) % step != 0) {
                    val up = ((shown - sliderMin) / step + 1) * step + sliderMin
                    val down = ((shown - sliderMin) / step) * step + sliderMin
                    if (raw > shown) up.coerceAtMost(sliderMax) else if (raw < shown) down.coerceAtLeast(sliderMin) else shown
                } else snapped
            },
            onValueChangeFinished = { onValueChange(shown) },
            valueRange = sliderMin.toFloat()..sliderMax.toFloat(),
            enabled = enabled,
        )
    }

    if (showDialog) {
        var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(shown.toString()) }
        val parsed = text.toIntOrNull()
        val valid = parsed != null && parsed in inputMin..inputMax
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { v -> if (v.length <= 5 && v.all { it.isDigit() }) text = v },
                    singleLine = true,
                    isError = !valid,
                    supportingText = { Text("$inputMin–$inputMax $unit") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                )
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    shown = parsed!!
                    onValueChange(parsed)
                    showDialog = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
}
