package com.cefrium.sample.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cefrium.sample.R
import com.cefrium.sample.data.BrowserPreferences
import com.cefrium.sample.engine.HardwareTelemetry
import com.cefrium.sample.engine.ThermalLevel
import com.cefrium.sample.theme.LocalSamuraiPalette
import com.cefrium.sample.theme.SamuraiCard
import com.cefrium.sample.theme.seigaihaBackdrop
import com.cefrium.sample.viewmodel.AssistantEntry
import kotlin.math.roundToInt

/**
 * Animated floating KoKoro mascot button with Samurai breathing levitation,
 * state-reactive rotating katana aura ring, and drag positioning.
 */
@Composable
fun FloatingKoKoroEmblem(
    boostActive: Boolean,
    incognito: Boolean,
    zenReaderActive: Boolean,
    animationsEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val samurai = LocalSamuraiPalette.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val transition = rememberInfiniteTransition(label = "kokoro_mascot_anim")
    val floatY by transition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kokoro_float",
    )
    val auraRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (boostActive) 3200 else 7200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "kokoro_aura",
    )
    val breatheScale by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kokoro_breathe",
    )

    val ringPrimary = when {
        boostActive -> samurai.boostActive
        incognito -> samurai.incognitoAccent
        zenReaderActive -> MaterialTheme.colorScheme.tertiary
        else -> samurai.katanaPrimary
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .graphicsLayer {
                translationY = if (animationsEnabled) floatY else 0f
                scaleX = if (animationsEnabled) breatheScale else 1f
                scaleY = if (animationsEnabled) breatheScale else 1f
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX = (offsetX + dragAmount.x).coerceIn(-650f, 80f)
                    offsetY = (offsetY + dragAmount.y).coerceIn(-900f, 120f)
                }
            }
            .size(62.dp)
            .clickable(onClick = onClick)
            .testTag("floating_kokoro_mascot"),
        contentAlignment = Alignment.Center,
    ) {
        // Rotating Samurai katana energy halo
        Canvas(modifier = Modifier.matchParentSize()) {
            rotate(if (animationsEnabled) auraRotation else 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            ringPrimary,
                            samurai.goldAccent,
                            Color.Transparent,
                        ),
                    ),
                    startAngle = 0f,
                    sweepAngle = 290f,
                    useCenter = false,
                    style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        // Mascot portrait
        Image(
            painter = painterResource(id = R.drawable.img_kokoro_mascot),
            contentDescription = "Apri Assistente KoKoro",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .border(1.5.dp, samurai.goldAccent.copy(alpha = 0.85f), CircleShape),
        )

        if (boostActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(samurai.boostActive)
                    .border(1.dp, MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = "Boost attivo",
                    tint = Color.Black,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

/**
 * Fast local KoKoro Brand Assistant + Live Gaming HUD Command Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KoKoroAssistantSheet(
    prefs: BrowserPreferences,
    telemetry: HardwareTelemetry,
    entries: List<AssistantEntry>,
    activePageTitle: String,
    activeTabsCount: Int,
    onExecuteCommand: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val samurai = LocalSamuraiPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var commandText by remember { mutableStateOf("") }

    val transition = rememberInfiniteTransition(label = "kokoro_sheet_avatar")
    val pulse by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kokoro_sheet_pulse",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .seigaihaBackdrop(samurai.seigaihaStroke)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header with animated KoKoro portrait & contextual real status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer {
                            if (prefs.samuraiAnimations) {
                                scaleX = pulse
                                scaleY = pulse
                            }
                        }
                        .clip(CircleShape)
                        .border(
                            2.dp,
                            Brush.sweepGradient(listOf(samurai.goldAccent, samurai.katanaPrimary, samurai.goldAccent)),
                            CircleShape,
                        ),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_kokoro_mascot),
                        contentDescription = "KoKoro Assistente",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "KoKoro · Assistente Samurai",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = if (telemetry.boostActive) {
                            "Boost attivo a ${telemetry.currentHz} Hz · Pronto ai tuoi ordini"
                        } else {
                            "Scheda: ${activePageTitle.ifBlank { "Nuova scheda" }.take(28)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = samurai.goldAccent,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Chiudi Assistente")
                }
            }

            // Live Gaming HUD Strip (real hardware telemetry)
            val thermalColor = when (telemetry.thermalLevel) {
                ThermalLevel.OPTIMAL -> samurai.thermalOk
                ThermalLevel.WARM -> samurai.thermalWarm
                ThermalLevel.HOT, ThermalLevel.CRITICAL -> samurai.thermalHot
            }
            SamuraiCard(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                borderColor = samurai.goldAccent.copy(alpha = 0.55f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HudMetric("HZ", "${telemetry.currentHz}", samurai.goldAccent)
                    HudMetric("FPS", "${telemetry.liveFps}", MaterialTheme.colorScheme.primary)
                    HudMetric("RAM", "${telemetry.availRamMb}MB", MaterialTheme.colorScheme.onSurface)
                    HudMetric("TERM", telemetry.thermalLevel.label, thermalColor)
                    HudMetric("TAB", "$activeTabsCount", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Quick Samurai Command Chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val quickCommands = listOf(
                    "Boost 120 Hz" to "boost 120",
                    "HUD Live" to "hud",
                    "Lettura Zen" to "lettura zen",
                    "Desktop" to "desktop",
                    "Pulisci RAM" to "pulisci ram",
                    "Tema Kurogane" to "kurogane",
                    "Tema Murasaki" to "murasaki",
                    "Stato Sistema" to "stato",
                )
                quickCommands.forEach { (label, cmd) ->
                    Surface(
                        onClick = { onExecuteCommand(cmd) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, samurai.goldAccent.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("assistant_chip_${cmd.replace(' ', '_')}"),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }
            }

            // Command Log
            SamuraiCard(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 220.dp),
            ) {
                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = "Comandi locali istantanei: scrivi \"boost 120\", \"lettura zen\", \"desktop\", \"pulisci ram\", \"privata\", \"tema kurogane\" oppure un URL/ricerca.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(entries, key = { it.id }) { msg ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (msg.fromUser) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = if (!msg.fromUser) BorderStroke(0.75.dp, samurai.goldAccent.copy(alpha = 0.4f)) else null,
                                ) {
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Command Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = commandText,
                    onValueChange = { commandText = it },
                    placeholder = { Text("Comando KoKoro o ricerca rapida…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (commandText.isNotBlank()) {
                                onExecuteCommand(commandText)
                                commandText = ""
                            }
                        },
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("assistant_command_input"),
                )
                Surface(
                    onClick = {
                        if (commandText.isNotBlank()) {
                            onExecuteCommand(commandText)
                            commandText = ""
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("assistant_send_button"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Esegui comando",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HudMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
    }
}
