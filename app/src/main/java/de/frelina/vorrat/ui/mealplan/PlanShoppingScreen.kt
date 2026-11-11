package de.frelina.vorrat.ui.mealplan

import de.frelina.vorrat.ui.components.GlassCard
import de.frelina.vorrat.ui.theme.VorratTheme
import androidx.compose.ui.text.font.FontWeight
import de.frelina.vorrat.ui.components.GlassIconButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.frelina.vorrat.R
import de.frelina.vorrat.domain.LineHint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanShoppingScreen(
    onBack: () -> Unit,
    viewModel: PlanShoppingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.planshop_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack, Modifier.padding(start = 8.dp))
                },
                actions = {
                    if (state.lines.isNotEmpty()) {
                        TextButton(onClick = { viewModel.setAll(true) }) { Text(stringResource(R.string.planshop_all)) }
                        TextButton(onClick = { viewModel.setAll(false) }) { Text(stringResource(R.string.planshop_none)) }
                    }
                },
            )
        },
        bottomBar = {
            if (state.lines.isNotEmpty()) {
                Button(
                    onClick = { viewModel.apply(onBack) },
                    enabled = state.checkedCount > 0,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text(stringResource(R.string.planshop_apply, state.checkedCount)) }
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.lines.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.planshop_empty), textAlign = TextAlign.Center)
            }
            else -> LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.lines, key = { it.line.key }) { row ->
                    LineRow(
                        row = row,
                        onToggle = { viewModel.toggle(row.line.key, it) },
                        onStaple = { viewModel.addStaple(row.line) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LineRow(row: PlanLineUi, onToggle: (Boolean) -> Unit, onStaple: () -> Unit) {
    val line = row.line
    val hint = when (val h = line.hint) {
        is LineHint.InPantry -> stringResource(R.string.planshop_hint_pantry, h.productName)
        LineHint.OnList -> stringResource(R.string.planshop_hint_list)
        LineHint.Staple -> stringResource(R.string.planshop_hint_staple)
        LineHint.Basic -> stringResource(R.string.planshop_hint_basic)
        null -> null
    }
    val title = listOfNotNull(line.amount?.takeIf(String::isNotBlank), line.name).joinToString(" ")
    GlassCard(Modifier.fillMaxWidth()) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle(!row.checked) }.padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = row.checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.planshop_for, line.recipes.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = VorratTheme.colors.textSecondary,
            )
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = VorratTheme.colors.accent)
            }
        }
        if (line.hint != LineHint.Staple) {
            IconButton(onClick = onStaple) {
                Icon(Icons.Outlined.Home, contentDescription = stringResource(R.string.planshop_staple_action_for, line.name))
            }
        }
    }
    }
}
