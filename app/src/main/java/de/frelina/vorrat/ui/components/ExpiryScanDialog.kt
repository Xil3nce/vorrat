package de.frelina.vorrat.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.util.Size
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import de.frelina.vorrat.R
import de.frelina.vorrat.domain.ExpiryDateParser
import de.frelina.vorrat.domain.ExpiryDateStabilizer
import de.frelina.vorrat.domain.Validation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDate
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Ablaufdatum per Kamera lesen (ML Kit Texterkennung, nur auf dem Gerät). Das erkannte Datum wird angezeigt
 * und erst mit "Übernehmen" ins Formular geschrieben.
 */
@Composable
fun ExpiryScanDialog(
    onDismiss: () -> Unit,
    onDate: (LocalDate) -> Unit,
    title: String? = null,
    onSkip: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) onDismiss()
    }
    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }

    var detected by remember { mutableStateOf<LocalDate?>(null) }
    var torch by remember { mutableStateOf(false) }
    val stabilizer = remember { ExpiryDateStabilizer() }
    // Zielrahmen relativ zur Vorschau (0..1), damit nur Text im Rahmen ausgewertet wird.
    var frameRegion by remember { mutableStateOf<RectF?>(null) }
    val frameHeightPx = with(LocalDensity.current) { FRAME_HEIGHT.toPx() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                    Text(
                        title ?: stringResource(R.string.expiry_scan_title),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { torch = !torch }) {
                        Icon(
                            if (torch) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,
                            contentDescription = stringResource(if (torch) R.string.scan_torch_off else R.string.scan_torch_on),
                        )
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds()
                        .onSizeChanged { size ->
                            if (size.width > 0 && size.height > 0) {
                                val h = (frameHeightPx / size.height).coerceAtMost(1f)
                                frameRegion = RectF(
                                    (1f - FRAME_WIDTH_FRACTION) / 2f,
                                    (1f - h) / 2f,
                                    (1f + FRAME_WIDTH_FRACTION) / 2f,
                                    (1f + h) / 2f,
                                )
                            }
                        },
                ) {
                    if (hasPermission) {
                        TextCameraPreview(
                            torchOn = torch,
                            region = frameRegion,
                            onText = { text -> detected = stabilizer.offer(ExpiryDateParser.parse(text)) },
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Zielrahmen
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth(FRAME_WIDTH_FRACTION)
                                .height(FRAME_HEIGHT)
                                .border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium),
                        )
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val date = detected
                    Text(
                        if (date != null) {
                            stringResource(R.string.expiry_scan_found, Validation.formatDate(date))
                        } else {
                            stringResource(R.string.expiry_scan_hint)
                        },
                        style = if (date != null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onSkip != null) {
                            OutlinedButton(onClick = onSkip, modifier = Modifier.height(56.dp)) {
                                Text(stringResource(R.string.expiry_scan_skip))
                            }
                        } else {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.height(56.dp)) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                        Button(
                            onClick = { date?.let(onDate) },
                            enabled = date != null,
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) { Text(stringResource(R.string.expiry_scan_apply)) }
                    }
                }
            }
        }
    }
}

private const val FRAME_WIDTH_FRACTION = 0.85f
private val FRAME_HEIGHT = 96.dp

/** Analyseauflösung: ML Kit braucht rund 16 px Zeichenhöhe, bei 640×480 ist ein kleines MHD zu grob. */
private val ANALYSIS_SIZE = Size(1920, 1080)

/**
 * Kameravorschau, die erkannten Text laufend an [onText] meldet. Ist [region] gesetzt (Anteile der Vorschau,
 * 0..1), werden nur Textzeilen in diesem Bereich (großzügig erweitert) gemeldet. Tippen stellt scharf.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
private fun TextCameraPreview(
    torchOn: Boolean,
    region: RectF?,
    onText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var camera by remember { mutableStateOf<Camera?>(null) }
    val currentCamera by rememberUpdatedState(camera)
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    DisposableEffect(previewView) {
        // Tippen zum Scharfstellen auf die angetippte Stelle
        previewView.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                currentCamera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                view.performClick()
            }
            true
        }
        onDispose { previewView.setOnTouchListener(null) }
    }
    val currentOnText by rememberUpdatedState(onText)
    val currentRegion by rememberUpdatedState(region)
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(lifecycleOwner) {
        val provider = suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(context))
        }
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(ANALYSIS_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                    )
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        analysis.setAnalyzer(executor) { proxy ->
            val media = proxy.image
            if (media == null) {
                proxy.close()
                return@setAnalyzer
            }
            val rotation = proxy.imageInfo.rotationDegrees
            // Bildgröße aufrecht (so liefert ML Kit die Koordinaten)
            val uprightW = if (rotation % 180 == 0) proxy.width else proxy.height
            val uprightH = if (rotation % 180 == 0) proxy.height else proxy.width
            val viewW = previewView.width
            val viewH = previewView.height
            val target = currentRegion?.let { r -> regionInImage(r, viewW, viewH, uprightW, uprightH) }
            val input = InputImage.fromMediaImage(media, rotation)
            recognizer.process(input)
                .addOnSuccessListener { result ->
                    val lines = result.textBlocks.flatMap { it.lines }
                        .filter { line ->
                            val box = line.boundingBox
                            target == null || box == null || target.contains(box.exactCenterX(), box.exactCenterY())
                        }
                        .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                    val text = lines.joinToString("\n") { it.text }
                    if (text.isNotBlank()) mainExecutor.execute { currentOnText(text) }
                }
                .addOnCompleteListener { proxy.close() }
        }
        provider.unbindAll()
        val bound = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        camera = bound
        // Auf die Bildmitte (Zielrahmen) scharfstellen und belichten
        val center = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
        bound.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(center).build())
    }

    LaunchedEffect(camera, torchOn) {
        camera?.let { c -> if (c.cameraInfo.hasFlashUnit()) c.cameraControl.enableTorch(torchOn) }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            recognizer.close()
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/**
 * Rechnet den Zielrahmen (Anteile der Vorschau) in Bildkoordinaten um. Die Vorschau zeigt das Bild mit
 * FILL_CENTER (skaliert und mittig beschnitten). Der Rahmen wird nach oben/unten um die eigene Höhe und seitlich
 * etwas erweitert, damit ungenaues Zielen nicht schadet.
 */
private fun regionInImage(region: RectF, viewW: Int, viewH: Int, imageW: Int, imageH: Int): RectF? {
    if (viewW <= 0 || viewH <= 0 || imageW <= 0 || imageH <= 0) return null
    val scale = maxOf(viewW.toFloat() / imageW, viewH.toFloat() / imageH)
    val offsetX = (viewW - imageW * scale) / 2f
    val offsetY = (viewH - imageH * scale) / 2f
    fun x(f: Float) = (f * viewW - offsetX) / scale
    fun y(f: Float) = (f * viewH - offsetY) / scale
    val h = region.height()
    val w = region.width()
    return RectF(x(region.left - w * 0.05f), y(region.top - h), x(region.right + w * 0.05f), y(region.bottom + h))
}
