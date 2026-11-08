package de.frelina.vorrat.ui.recipes

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import de.frelina.vorrat.R
import de.frelina.vorrat.domain.Recipe
import de.frelina.vorrat.domain.RecipeMatcher
import java.time.LocalDate

/**
 * "läuft morgen ab" u. Ä.; null ohne Ablaufdatum bzw. – mit [onlySoon] – wenn es nicht innerhalb von
 * [RecipeMatcher.RESCUE_DAYS] Tagen abläuft.
 */
@Composable
fun expiryPhrase(expiry: LocalDate?, today: LocalDate, onlySoon: Boolean = false): String? {
    if (expiry == null) return null
    val days = RecipeMatcher.daysUntil(expiry, today)
    if (onlySoon && days > RecipeMatcher.RESCUE_DAYS) return null
    return when {
        days < 0 -> stringResource(R.string.recipe_expired)
        days == 0L -> stringResource(R.string.recipe_expires_today)
        days == 1L -> stringResource(R.string.recipe_expires_tomorrow)
        else -> pluralStringResource(R.plurals.recipe_expires_in_days, days.toInt(), days.toInt())
    }
}

/** "30 Min. · 2 Portionen · vegetarisch". */
@Composable
fun recipeMeta(recipe: Recipe): String {
    val parts = buildList {
        recipe.minutes?.let { add(stringResource(R.string.recipe_minutes, it)) }
        recipe.servings?.let { add(pluralStringResource(R.plurals.recipe_servings, it, it)) }
        if (recipe.vegetarian) add(stringResource(R.string.recipe_vegetarian))
        if (!recipe.builtin) add(stringResource(R.string.recipe_own))
    }
    return parts.joinToString(" · ")
}
