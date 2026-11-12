package de.frelina.vorrat.ui.household

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R
import de.frelina.vorrat.domain.InviteCode

/** Erststart ohne Haushalt: neuen Haushalt anlegen oder mit Einladungscode beitreten. */
@Composable
fun OnboardingScreen(viewModel: HouseholdViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lastCode by viewModel.lastCode.collectAsStateWithLifecycle()
    val removed by viewModel.removedNotice.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }
    var codeTouched by rememberSaveable { mutableStateOf(false) }
    // Zuletzt verlassener Haushalt: Code vorschlagen (nach dem Entfernen ist er ungültig – dann nicht).
    LaunchedEffect(lastCode, removed) {
        if (!codeTouched) code = lastCode?.takeIf { !removed }?.let(InviteCode::format).orEmpty()
    }
    var confirmJoin by rememberSaveable { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Icon(
                Icons.Outlined.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally),
            )
            Text(
                stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(stringResource(R.string.onboarding_text))
            if (removed) {
                Text(
                    stringResource(R.string.household_removed_notice),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(onClick = viewModel::create, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_create))
            }
            Text(stringResource(R.string.onboarding_create_hint), style = MaterialTheme.typography.bodySmall)

            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f))
                Text(
                    stringResource(R.string.onboarding_or),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(Modifier.weight(1f))
            }

            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it.take(MAX_INPUT)
                    codeTouched = true
                    viewModel.clearError()
                },
                label = { Text(stringResource(R.string.onboarding_code_label)) },
                placeholder = { Text(stringResource(R.string.onboarding_code_placeholder)) },
                singleLine = true,
                enabled = !state.busy,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (code.isNotBlank()) confirmJoin = true }),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { confirmJoin = true },
                enabled = !state.busy && code.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_join))
            }

            state.error?.let { error ->
                Text(error.let { stringResource(it) }, color = MaterialTheme.colorScheme.error)
            }

            Text(stringResource(R.string.onboarding_privacy), style = MaterialTheme.typography.bodySmall)
        }
    }

    if (confirmJoin) {
        AlertDialog(
            onDismissRequest = { confirmJoin = false },
            title = { Text(stringResource(R.string.onboarding_join_confirm_title)) },
            text = { Text(stringResource(R.string.onboarding_join_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmJoin = false
                    viewModel.join(code)
                }) { Text(stringResource(R.string.onboarding_join_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmJoin = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private const val MAX_INPUT = 20
