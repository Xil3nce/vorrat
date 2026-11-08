package de.frelina.vorrat.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.frelina.vorrat.R
import de.frelina.vorrat.domain.IngredientGroups
import de.frelina.vorrat.domain.IngredientMatcher

/**
 * Auswahl einer Zutatengruppe: Dropdown, alphabetisch, mit Suche (Tippen filtert die Liste).
 * Die erste Option ist "ohne Auswahl" ([nullOptionLabel], z. B. "Automatisch (erkannt: Nudeln)").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientGroupField(
    label: String,
    selected: String?,
    nullOptionLabel: String,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: (@Composable () -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val shown = selected?.let { IngredientGroups.label(it) } ?: nullOptionLabel
    val normalizedQuery = IngredientMatcher.normalize(query)
    val options = remember(normalizedQuery) {
        IngredientGroups.sortedByLabel.filter {
            normalizedQuery.isEmpty() || IngredientMatcher.normalize(it.label).contains(normalizedQuery)
        }
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            expanded = it
            if (!it) query = ""
        },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = if (expanded) query else shown,
            onValueChange = { query = it; expanded = true },
            label = { Text(label) },
            placeholder = if (expanded) ({ Text(shown) }) else null,
            singleLine = true,
            supportingText = supportingText,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false; query = "" }) {
            if (normalizedQuery.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(nullOptionLabel) },
                    onClick = { onSelect(null); expanded = false; query = "" },
                )
            }
            options.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.label) },
                    onClick = { onSelect(group.id); expanded = false; query = "" },
                )
            }
            if (options.isEmpty()) {
                DropdownMenuItem(text = { Text(stringResource(R.string.recipe_group_no_match)) }, onClick = {}, enabled = false)
            }
        }
    }
}
