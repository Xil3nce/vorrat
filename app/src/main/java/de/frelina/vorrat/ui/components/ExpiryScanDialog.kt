package de.frelina.vorrat.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
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
import androidx.compose.ui.platform.LocalContext
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
fun ExpiryScanDialog(onDismiss: () -> Unit, onDate: (LocalDate) -> Unit) {
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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                    Text(
                        stringResource(R.string.expiry_scan_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { torch = !torch }) {
                        Icon(
                            if (torch) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,
                            contentDescription = stringResource(if (torch) R.string.scan_torch_off else R.string.scan_torch_on),
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                    if (hasPermission) {
                        TextCameraPreview(
                            torchOn = torch,
                            onText = { text -> ExpiryDateParser.parse(text)?.let { detected = it } },
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Zielrahmen
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth(0.85f)
                                .height(96.dp)
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
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.height(56.dp)) {
                            Text(stringResource(R.string.action_cancel))
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

/** Kameravorschau, die erkannten Text laufend an [onText] meldet. */
@OptIn(ExperimentalGetImage::class)
@Composable
private fun TextCameraPreview(torchOn: Boolean, onText: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val currentOnText by rememberUpdatedState(onText)
    var camera by remember { mutableStateOf<Camera?>(null) }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(lifecycleOwner) {
        val provider = suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(context))
        }
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        analysis.setAnalyzer(executor) { proxy ->
            val media = proxy.image
            if (media == null) {
                proxy.close()
                return@setAnalyzer
            }
            val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
            recognizer.process(input)
                .addOnSuccessListener { result ->
                    val text = result.text
                    if (text.isNotBlank()) mainExecutor.execute { currentOnText(text) }
                }
                .addOnCompleteListener { proxy.close() }
        }
        provider.unbindAll()
        camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
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
