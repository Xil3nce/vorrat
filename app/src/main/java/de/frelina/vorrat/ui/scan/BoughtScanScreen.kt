package de.frelina.vorrat.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.frelina.vorrat.R
import de.frelina.vorrat.ui.components.GlassIconButton
import de.frelina.vorrat.ui.theme.VorratTheme

/**
 * Artikel von der Einkaufsliste als gekauft erfassen: zuerst den Barcode scannen (Formular wird dann aus
 * Open Food Facts vorbelegt). Ohne Barcode (z. B. loses Obst) geht es über den Button direkt zur manuellen Anlage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoughtScanScreen(
    itemName: String,
    onBack: () -> Unit,
    onBarcode: (String) -> Unit,
    onManual: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }
    var torch by rememberSaveable { mutableStateOf(false) }
    var typeDialog by rememberSaveable { mutableStateOf(false) }
    // Nur den ersten erkannten Barcode verwenden (die Analyse liefert sonst mehrfach hintereinander).
    var done by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    fun finish(code: String) {
        if (done) return
        done = true
        onBarcode(code)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.bought_scan_title, itemName),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    GlassIconButton(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        stringResource(R.string.action_back),
                        onBack,
                        Modifier.padding(start = 8.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.Black)
                    .clipToBounds(),
            ) {
                if (hasPermission) {
                    CameraPreview(
                        analyzing = !done,
                        torchOn = torch,
                        onBarcode = ::finish,
                        modifier = Modifier.fillMaxSize(),
                    )
                    ScanFrame(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 28.dp))
                    GlassIconButton(
                        if (torch) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,
                        stringResource(if (torch) R.string.scan_torch_off else R.string.scan_torch_on),
                        onClick = { torch = !torch },
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                    )
                    Text(
                        stringResource(R.string.bought_scan_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = VorratTheme.colors.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                } else {
                    PermissionRequest(
                        denied = permissionDenied,
                        onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onOpenSettings = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        },
                    )
                }
            }
            Button(onClick = onManual, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.bought_scan_manual))
            }
            TextButton(
                onClick = { typeDialog = true },
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            ) {
                Icon(Icons.Outlined.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.scan_enter_barcode))
            }
        }
    }

    if (typeDialog) {
        ManualBarcodeDialog(
            onDismiss = { typeDialog = false },
            onConfirm = { typeDialog = false; finish(it) },
        )
    }
}
