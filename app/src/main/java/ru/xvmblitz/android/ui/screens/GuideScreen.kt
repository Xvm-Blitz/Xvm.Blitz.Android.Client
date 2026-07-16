package ru.xvmblitz.android.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private data class GuideStep(
    val title: String,
    val description: String,
    val illustration: GuideIllustration,
)

private enum class GuideIllustration {
    Auth,
    Fab,
    Capture,
    Panels,
    HideStats,
    ConfigMove,
    ConfigResize,
    Updates,
}

private val GuideSteps = listOf(
    GuideStep(
        title = "Авторизация",
        description = "Откройте «Профиль» / «Войти» и введите API‑ключ. После входа можно смотреть квоту и сменить ключ.",
        illustration = GuideIllustration.Auth,
    ),
    GuideStep(
        title = "Кнопка поверх игры",
        description = "Включите «Кнопка поверх экрана». Перетащите кнопку в удобное место. Короткий тап запускает захват статистики.",
        illustration = GuideIllustration.Fab,
    ),
    GuideStep(
        title = "Считать статистику",
        description = "Откройте экран загрузки боя в Tanks Blitz и нажмите «Статистика» на плавающей кнопке или «Считать статистику» в приложении.",
        illustration = GuideIllustration.Capture,
    ),
    GuideStep(
        title = "Панели оверлея",
        description = "Появятся таблицы союзников и противников поверх игры. У противников колонки зеркальные. Длинный текст обрезается с «…».",
        illustration = GuideIllustration.Panels,
    ),
    GuideStep(
        title = "Скрыть статистику",
        description = "Чтобы закончить бой, зажмите палец на панели союзников или противников и в меню выберите «Скрыть статистику».",
        illustration = GuideIllustration.HideStats,
    ),
    GuideStep(
        title = "Перемещение панелей",
        description = "Включите «Режим настройки панелей» и перетащите таблицу за её тело, чтобы выбрать позицию на экране.",
        illustration = GuideIllustration.ConfigMove,
    ),
    GuideStep(
        title = "Размер панелей",
        description = "В режиме настройки: правый край — ширина, нижний — высота и шрифт, угол — оба направления. Ширина следует за размером шрифта.",
        illustration = GuideIllustration.ConfigResize,
    ),
    GuideStep(
        title = "Обновления",
        description = "В карточке «Обновление» можно проверить новую версию и установить APK прямо из приложения.",
        illustration = GuideIllustration.Updates,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var stepIndex by remember { mutableIntStateOf(0) }
    val step = GuideSteps[stepIndex]
    val isLast = stepIndex == GuideSteps.lastIndex

    LaunchedEffect(stepIndex) {
        delay(5200)
        if (stepIndex < GuideSteps.lastIndex) {
            stepIndex += 1
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Инструкция") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                GuideSteps.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == stepIndex) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == stepIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                },
                            ),
                    )
                }
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
                },
                label = "guide-step",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { current ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GuideIllustrationBox(
                        illustration = current.illustration,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface),
                    )
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = current.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        if (stepIndex == 0) onBack() else stepIndex -= 1
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (stepIndex == 0) "Закрыть" else "Назад")
                }
                Button(
                    onClick = {
                        if (isLast) onFinished() else stepIndex += 1
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isLast) "Готово" else "Далее")
                }
            }
        }
    }
}

@Composable
private fun GuideIllustrationBox(
    illustration: GuideIllustration,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (illustration) {
            GuideIllustration.Auth -> AuthIllustration()
            GuideIllustration.Fab -> FabIllustration()
            GuideIllustration.Capture -> CaptureIllustration()
            GuideIllustration.Panels -> PanelsIllustration()
            GuideIllustration.HideStats -> HideStatsIllustration()
            GuideIllustration.ConfigMove -> ConfigMoveIllustration()
            GuideIllustration.ConfigResize -> ConfigResizeIllustration()
            GuideIllustration.Updates -> UpdatesIllustration()
        }
    }
}

@Composable
private fun AuthIllustration() {
    val pulse by rememberInfiniteTransition(label = "auth").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "auth-pulse",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(44.dp)
                .scale(pulse)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "  ••••••••‑API‑KEY",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(40.dp)
                .scale(pulse)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("Войти", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun FabIllustration() {
    val drift by rememberInfiniteTransition(label = "fab").animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "fab-drift",
    )
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-28).dp, y = drift.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xE63D7EA6))
                .padding(horizontal = 9.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Статистика",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun HideStatsIllustration() {
    val press by rememberInfiniteTransition(label = "hide").animateFloat(
        initialValue = 1f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "hide-press",
    )
    val menuAlpha by rememberInfiniteTransition(label = "menu").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "menu-alpha",
    )
    Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        MiniPanel(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(140.dp)
                .scale(press),
            mirrored = false,
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .alpha(menuAlpha)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Скрыть статистику", color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun CaptureIllustration() {
    val flash by rememberInfiniteTransition(label = "capture").animateFloat(
        initialValue = 0.15f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "capture-flash",
    )
    Canvas(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        drawRoundRect(
            color = Color(0xFF2A3140),
            cornerRadius = CornerRadius(24f, 24f),
            size = size,
        )
        drawRoundRect(
            color = Color.White.copy(alpha = flash),
            topLeft = Offset(size.width * 0.12f, size.height * 0.18f),
            size = Size(size.width * 0.76f, size.height * 0.55f),
            cornerRadius = CornerRadius(12f, 12f),
        )
        drawCircle(
            color = Color(0xFF3D7EA6),
            radius = size.minDimension * 0.08f,
            center = Offset(size.width * 0.5f, size.height * 0.82f),
        )
    }
}

@Composable
private fun PanelsIllustration() {
    val alpha by rememberInfiniteTransition(label = "panels").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "panels-alpha",
    )
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniPanel(modifier = Modifier.weight(1f).alpha(alpha), mirrored = false)
        MiniPanel(modifier = Modifier.weight(1f).alpha(alpha), mirrored = true)
    }
}

@Composable
private fun MiniPanel(modifier: Modifier = Modifier, mirrored: Boolean) {
    Column(
        modifier = modifier
            .height(120.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x80000000))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(4) { index ->
            val color = when (index % 3) {
                0 -> Color(0xFFD68585)
                1 -> Color(0xFF93CF93)
                else -> Color(0xFF85BBF2)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x14000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (mirrored) {
                    Text("%.0f%%".format(48f + index * 6), color = color, style = MaterialTheme.typography.labelSmall)
                    Text("Tank", color = Color.White, style = MaterialTheme.typography.labelSmall)
                } else {
                    Text("Player", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    Text("%.0f%%".format(48f + index * 6), color = color, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ConfigMoveIllustration() {
    val drift by rememberInfiniteTransition(label = "move").animateFloat(
        initialValue = -14f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "move-drift",
    )
    Box(modifier = Modifier.fillMaxSize().padding(28.dp)) {
        Box(
            modifier = Modifier
                .offset(x = drift.dp, y = (-drift / 2).dp)
                .align(Alignment.Center)
                .width(160.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x80000000)),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
            drawLine(
                color = Color(0xFF3D7EA6),
                start = Offset(size.width * 0.3f, size.height * 0.7f),
                end = Offset(size.width * 0.7f, size.height * 0.35f),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
                pathEffect = pathEffect,
            )
        }
    }
}

@Composable
private fun ConfigResizeIllustration() {
    val grow by rememberInfiniteTransition(label = "resize").animateFloat(
        initialValue = 0.78f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "resize-grow",
    )
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .width((180 * grow).dp)
                .height((100 * grow).dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x80000000)),
        )
        Canvas(
            modifier = Modifier
                .width((180 * grow).dp)
                .height((100 * grow).dp),
        ) {
            val stroke = Stroke(width = 4f, cap = StrokeCap.Round)
            drawLine(
                color = Color.White.copy(alpha = 0.85f),
                start = Offset(size.width - 18f, size.height - 8f),
                end = Offset(size.width - 8f, size.height - 18f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.85f),
                start = Offset(size.width - 26f, size.height - 8f),
                end = Offset(size.width - 8f, size.height - 26f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun UpdatesIllustration() {
    val progress by rememberInfiniteTransition(label = "updates").animateFloat(
        initialValue = 0.15f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
        label = "updates-progress",
    )
    Column(
        modifier = Modifier.padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Доступна версия 1.2.0", color = MaterialTheme.colorScheme.onSurface)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Text("Загрузка обновления…", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}
