package de.frelina.vorrat.ui.settings

import androidx.compose.ui.text.font.FontWeight
import de.frelina.vorrat.ui.components.GlassIconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.fillMaxWidth
import de.frelina.vorrat.ui.components.GlassCard
import de.frelina.vorrat.ui.theme.VorratTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.frelina.vorrat.BuildConfig
import de.frelina.vorrat.R

private const val OFF_URL = "https://world.openfoodfacts.org"
private const val ODBL_URL = "https://opendatacommons.org/licenses/odbl/1-0/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack, Modifier.padding(start = 8.dp))
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), color = VorratTheme.colors.textSecondary)
                    Text(stringResource(R.string.about_description))
                }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.about_off_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.about_off_text))
                }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.about_privacy_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.about_privacy_sync))
                    Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodySmall, color = VorratTheme.colors.textSecondary)
                }
            }
            OutlinedButton(onClick = { uriHandler.openUri(OFF_URL) }) { Text(stringResource(R.string.about_off_link)) }
            OutlinedButton(onClick = { uriHandler.openUri(ODBL_URL) }) { Text(stringResource(R.string.about_license_link)) }
        }
    }
}
