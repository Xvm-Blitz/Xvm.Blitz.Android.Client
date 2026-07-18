package ru.xvmblitz.android.ui.screens

import android.content.res.Configuration
import android.widget.Toast
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.xvmblitz.android.overlay.OverlayTableBackground
import ru.xvmblitz.android.util.GamePacksHelper

private data class GuideStep(
    val title: String,
    val description: String,
    val illustration: GuideIllustration,
)

private enum class GuideIllustration {
    Auth,
    OverlayPermission,
    GameFiles,
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
        title = "Поверх других приложений",
        description = "Обязательно разрешите отображение поверх других приложений. Без этого кнопка «Статистика» и панели оверлея не будут отображаться нормально.",
        illustration = GuideIllustration.OverlayPermission,
    ),
    GuideStep(
        title = "Файлы в каталоге игры",
        description = """
            Без замены файлов распознавание работает крайне некорректно.
            Откройте Android/data/com.tanksblitz/files/packs/ (создайте папки, если их нет) и положите файлы:
            UI/Screens3 → Font.style.dvpl
            UI/Screens/Battle → BattleLoadingScreen.yaml.dvpl
            Fonts → Jost-Light.ttf.dvpl
            С замененным шрифтом некоторые надписи в интерфейсе игры могут отображаться некорректно.
            Чтобы вернуть поведение по умолчанию, просто удалите эти файлы.
        """.trimIndent(),
        illustration = GuideIllustration.GameFiles,
    ),
    GuideStep(
        title = "Кнопка поверх игры",
        description = "Кнопка «Статистика» всегда поверх игры. Перетащите её в удобное место. Короткий тап запускает захват статистики.",
        illustration = GuideIllustration.Fab,
    ),
    GuideStep(
        title = "Считать статистику",
        description = "Откройте экран загрузки боя в Tanks Blitz и нажмите «Статистика» на плавающей кнопке.",
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
        description = "Включите «Режим настройки панелей» (экран станет горизонтальным) и перетащите таблицу или задайте координаты вручную.",
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var stepIndex by remember { mutableIntStateOf(0) }
    var goingForward by remember { mutableStateOf(true) }
    var exportedFolder by remember { mutableStateOf<File?>(null) }
    var showOpenExportedFolderDialog by remember { mutableStateOf(false) }
    var showGameFilesCopyHelp by remember { mutableStateOf(false) }
    val step = GuideSteps[stepIndex]
    val isGameFilesStep = step.illustration == GuideIllustration.GameFiles
    val isLast = stepIndex == GuideSteps.lastIndex
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 56.dp.toPx() }

    fun goNext() {
        if (showGameFilesCopyHelp) {
            showGameFilesCopyHelp = false
        }
        if (isLast) {
            onFinished()
        } else {
            goingForward = true
            stepIndex += 1
        }
    }

    fun goPrevious() {
        if (showGameFilesCopyHelp) {
            showGameFilesCopyHelp = false
            return
        }
        if (stepIndex == 0) {
            onBack()
        } else {
            goingForward = false
            stepIndex -= 1
        }
    }

    BackHandler(onBack = ::goPrevious)

    fun openTanksFolder() {
        if (!GamePacksHelper.openTanksRootFolder(context)) {
            Toast.makeText(
                context,
                "Не удалось открыть папку игры. Откройте её вручную в проводнике.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun exportAndOfferFolder() {
        coroutineScope.launch {
            val folder = withContext(Dispatchers.IO) {
                runCatching { GamePacksHelper.exportPackFiles(context) }.getOrNull()
            }
            if (folder != null) {
                exportedFolder = folder
                showOpenExportedFolderDialog = true
            } else {
                Toast.makeText(context, "Не удалось сохранить файлы для копирования", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(stepIndex) {
        showGameFilesCopyHelp = false
        if (GuideSteps[stepIndex].illustration == GuideIllustration.GameFiles) {
            val folder = withContext(Dispatchers.IO) {
                runCatching { GamePacksHelper.exportPackFiles(context) }.getOrNull()
            }
            if (folder != null) {
                exportedFolder = folder
                showOpenExportedFolderDialog = true
            } else {
                Toast.makeText(context, "Не удалось сохранить файлы для копирования", Toast.LENGTH_LONG).show()
            }
            return@LaunchedEffect
        }
        delay(5_200L)
        if (stepIndex < GuideSteps.lastIndex) {
            goingForward = true
            stepIndex += 1
        }
    }

    if (showOpenExportedFolderDialog && exportedFolder != null) {
        AlertDialog(
            onDismissRequest = { showOpenExportedFolderDialog = false },
            title = { Text("Файлы сохранены") },
            text = {
                Text(
                    "Файлы для копирования сохранены в:\n${exportedFolder!!.absolutePath}\n\n" +
                        "Открыть эту папку в проводнике?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val folder = exportedFolder
                        showOpenExportedFolderDialog = false
                        if (folder == null) {
                            return@TextButton
                        }
                        coroutineScope.launch {
                            val opened = withContext(Dispatchers.IO) {
                                GamePacksHelper.openFolder(context, folder)
                            }
                            if (!opened) {
                                Toast.makeText(
                                    context,
                                    "Не удалось открыть папку. Путь: ${folder.absolutePath}",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                ) {
                    Text("Открыть")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOpenExportedFolderDialog = false }) {
                    Text("Позже")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Обучение") },
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
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    if (goingForward) {
                        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it / 3 } + fadeOut())
                    }
                },
                label = "guide-step",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(stepIndex, isLast, showGameFilesCopyHelp) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalDrag += dragAmount
                            },
                            onDragEnd = {
                                when {
                                    totalDrag <= -swipeThresholdPx -> goNext()
                                    totalDrag >= swipeThresholdPx -> goPrevious()
                                }
                            },
                            onDragCancel = { totalDrag = 0f },
                        )
                    },
            ) { current ->
                val showingCopyHelp =
                    showGameFilesCopyHelp && current.illustration == GuideIllustration.GameFiles
                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LandscapePhoneFrame(
                            modifier = Modifier
                                .weight(1.35f)
                                .fillMaxSize(),
                        ) {
                            GuideIllustrationBox(
                                illustration = current.illustration,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        GuideStepText(
                            title = if (showingCopyHelp) {
                                "Как скопировать файлы"
                            } else {
                                current.title
                            },
                            description = current.description,
                            isGameFilesStep = current.illustration == GuideIllustration.GameFiles,
                            showGameFilesCopyHelp = showingCopyHelp,
                            onOpenTanksFolder = ::openTanksFolder,
                            onShowCopyHelp = { showGameFilesCopyHelp = true },
                            onExportFiles = ::exportAndOfferFolder,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                            textAlign = TextAlign.Start,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        LandscapePhoneFrame(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.15f),
                        ) {
                            GuideIllustrationBox(
                                illustration = current.illustration,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        GuideStepText(
                            title = if (showingCopyHelp) {
                                "Как скопировать файлы"
                            } else {
                                current.title
                            },
                            description = current.description,
                            isGameFilesStep = current.illustration == GuideIllustration.GameFiles,
                            showGameFilesCopyHelp = showingCopyHelp,
                            onOpenTanksFolder = ::openTanksFolder,
                            onShowCopyHelp = { showGameFilesCopyHelp = true },
                            onExportFiles = ::exportAndOfferFolder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = ::goPrevious,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        when {
                            showGameFilesCopyHelp && isGameFilesStep -> "Назад"
                            stepIndex == 0 -> "Закрыть"
                            else -> "Назад"
                        },
                    )
                }
                Button(
                    onClick = ::goNext,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isLast) "Готово" else "Далее")
                }
            }
        }
    }
}

@Composable
private fun GuideStepText(
    title: String,
    description: String,
    isGameFilesStep: Boolean,
    showGameFilesCopyHelp: Boolean,
    onOpenTanksFolder: () -> Unit,
    onShowCopyHelp: () -> Unit,
    onExportFiles: () -> Unit,
    modifier: Modifier = Modifier,
    textAlign: TextAlign,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (textAlign == TextAlign.Center) {
            Alignment.CenterHorizontally
        } else {
            Alignment.Start
        },
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (isGameFilesStep && showGameFilesCopyHelp) {
            GameFilesCopyHelpDescription(
                textAlign = textAlign,
                onOpenTanksFolder = onOpenTanksFolder,
                onExportFiles = onExportFiles,
            )
        } else if (isGameFilesStep) {
            GameFilesDescription(
                textAlign = textAlign,
                onOpenTanksFolder = onOpenTanksFolder,
                onShowCopyHelp = onShowCopyHelp,
            )
        } else {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GameFilesDescription(
    textAlign: TextAlign,
    onOpenTanksFolder: () -> Unit,
    onShowCopyHelp: () -> Unit,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val bodyColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    val packsPath = "${GamePacksHelper.TANKS_PACKS_RELATIVE_PATH}/"
    val annotated = remember(linkColor, bodyColor, onOpenTanksFolder) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = bodyColor)) {
                append("Без замены файлов распознавание работает крайне некорректно.\n")
                append("Откройте ")
            }
            withLink(
                LinkAnnotation.Clickable(
                    tag = "tanks_folder",
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium,
                        ),
                    ),
                    linkInteractionListener = { onOpenTanksFolder() },
                ),
            ) {
                append(packsPath)
            }
            withStyle(SpanStyle(color = bodyColor)) {
                append(" (создайте папки, если их нет) и положите файлы:\n")
                append("UI/Screens3 → Font.style.dvpl\n")
                append("UI/Screens/Battle → BattleLoadingScreen.yaml.dvpl\n")
                append("Fonts → Jost-Light.ttf.dvpl\n")
                append("С замененным шрифтом некоторые надписи в интерфейсе игры могут отображаться некорректно.\n")
                append("Чтобы вернуть поведение по умолчанию, просто удалите эти файлы.")
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = if (textAlign == TextAlign.Center) {
            Alignment.CenterHorizontally
        } else {
            Alignment.Start
        },
    ) {
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium.copy(
                textAlign = textAlign,
                color = bodyColor,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onShowCopyHelp) {
            Text("Как скопировать файлы")
        }
    }
}

@Composable
private fun GameFilesCopyHelpDescription(
    textAlign: TextAlign,
    onOpenTanksFolder: () -> Unit,
    onExportFiles: () -> Unit,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val bodyColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    val packsPath = "${GamePacksHelper.TANKS_PACKS_RELATIVE_PATH}/"
    val exportPath = "Android/data/ru.xvmblitz.android/files/game_packs/"
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = if (textAlign == TextAlign.Center) {
            Alignment.CenterHorizontally
        } else {
            Alignment.Start
        },
    ) {
        Text(
            text = "Автоматически положить файлы в каталог игры нельзя из‑за ограничений Android. " +
                "Скопируйте их вручную одним из способов ниже.",
            style = MaterialTheme.typography.bodyMedium,
            color = bodyColor,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Способ 1. С телефона",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = bodyColor)) {
                    append("1. Нажмите «Сохранить файлы» — они появятся в\n")
                    append(exportPath)
                    append("\nуже с папками UI/Screens3, UI/Screens/Battle и Fonts.\n")
                    append("2. Откройте папку игры ")
                }
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "tanks_folder_help",
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium,
                            ),
                        ),
                        linkInteractionListener = { onOpenTanksFolder() },
                    ),
                ) {
                    append(packsPath)
                }
                withStyle(SpanStyle(color = bodyColor)) {
                    append("\n3. Скопируйте туда содержимое game_packs (папки UI и Fonts), " +
                        "сохраняя структуру.")
                }
            },
            style = MaterialTheme.typography.bodyMedium.copy(textAlign = textAlign),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Способ 2. Через USB с ПК",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "1. Подключите телефон к компьютеру (режим передачи файлов).\n" +
                "2. На ПК откройте $exportPath\n" +
                "3. Скопируйте папки UI и Fonts в\n$packsPath\n" +
                "с сохранением структуры подпапок.",
            style = MaterialTheme.typography.bodyMedium,
            color = bodyColor,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Чтобы вернуть поведение по умолчанию, просто удалите эти файлы из папки packs игры.",
            style = MaterialTheme.typography.bodyMedium,
            color = bodyColor,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = onExportFiles) {
            Text("Сохранить файлы")
        }
    }
}

@Composable
private fun LandscapePhoneFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxSize(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF1C212B))
                .padding(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 2.dp)
                    .width(3.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF2E3545)),
            )
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
            GuideIllustration.OverlayPermission -> OverlayPermissionIllustration()
            GuideIllustration.GameFiles -> GameFilesIllustration()
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
private fun GameFilesIllustration() {
    val pulse by rememberInfiniteTransition(label = "files").animateFloat(
        initialValue = 0.97f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "files-pulse",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .scale(pulse),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "packs/",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
            )
            GameFilePathRow("UI/Screens3/", "Font.style.dvpl")
            GameFilePathRow("UI/Screens/Battle/", "BattleLoadingScreen.yaml.dvpl")
            GameFilePathRow("Fonts/", "Jost-Light.ttf.dvpl")
            Text(
                text = "Без замены файлов получение статистики работает нестабильно",
                color = MaterialTheme.colorScheme.error,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 11.sp,
            )
        }
    }
}

@Composable
private fun GameFilePathRow(folder: String, file: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(folder, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), fontSize = 9.sp)
        Text(
            file,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
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
private fun OverlayPermissionIllustration() {
    val pulse by rememberInfiniteTransition(label = "overlay-permission").animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "overlay-permission-pulse",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .scale(pulse)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                .padding(horizontal = 14.dp, vertical = 16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Разрешить отображение поверх других приложений?",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = "Разрешить",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
        Text(
            text = "Без разрешения оверлей не отображается",
            color = MaterialTheme.colorScheme.error,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
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
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xE63D7EA6))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Статистика",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 8.sp,
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
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = Color(0xFF2A3140),
                cornerRadius = CornerRadius(16f, 16f),
                size = size,
            )
            drawRoundRect(
                color = Color.White.copy(alpha = flash),
                topLeft = Offset(size.width * 0.08f, size.height * 0.14f),
                size = Size(size.width * 0.84f, size.height * 0.62f),
                cornerRadius = CornerRadius(10f, 10f),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 14.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xE63D7EA6))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text("Статистика", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
        }
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
            .background(OverlayTableBackground)
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
                    .background(Color.Transparent, RoundedCornerShape(4.dp))
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
                .background(OverlayTableBackground),
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
                .background(OverlayTableBackground),
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
