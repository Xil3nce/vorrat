package de.frelina.vorrat.ui.components

import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Wisch-Zustand für Listeneinträge, die nach dem Wischen verschwinden und per "Rückgängig" mit derselben ID
 * zurückkommen können. Der Material-Zustand wird per rememberSaveable gespeichert, und die LazyColumn hebt ihn je
 * Schlüssel auf – ohne Zurücksetzen käme der wiederhergestellte Eintrag "weggewischt" (unsichtbar) zurück.
 * Erst ab 40 % der Breite auslösen (Standard 56 dp ist beim Scrollen zu empfindlich).
 */
@Composable
fun rememberListSwipeState(): SwipeToDismissBoxState {
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { total -> total * 0.4f })
    LaunchedEffect(Unit) {
        if (state.currentValue != SwipeToDismissBoxValue.Settled) state.snapTo(SwipeToDismissBoxValue.Settled)
    }
    return state
}
