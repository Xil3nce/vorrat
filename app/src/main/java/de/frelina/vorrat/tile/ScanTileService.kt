package de.frelina.vorrat.tile

import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import de.frelina.vorrat.MainActivity
import de.frelina.vorrat.R

/**
 * Schnellkachel "Scannen" in den Android-Schnelleinstellungen: ein Tipp öffnet Vorrat direkt im Scanner.
 * Bei gesperrtem Gerät fragt Android erst nach dem Entsperren.
 */
class ScanTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_scan_label)
            subtitle = getString(R.string.app_name)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { openScanner() } else openScanner()
    }

    private fun openScanner() {
        val pending = PendingIntent.getActivity(
            this,
            REQUEST_CODE,
            scanIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        startActivityAndCollapse(pending)
    }

    companion object {
        private const val REQUEST_CODE = 41

        fun scanIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_SCAN)
                .putExtra(MainActivity.EXTRA_OPEN_FILTER, MainActivity.OPEN_SCAN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /**
         * Android fragt, ob die Kachel zu den Schnelleinstellungen hinzugefügt werden soll.
         * [onResult] erhält true, wenn sie hinzugefügt wurde oder schon vorhanden ist.
         */
        fun requestAdd(context: Context, onResult: (Boolean) -> Unit) {
            val manager = context.getSystemService(StatusBarManager::class.java) ?: return onResult(false)
            manager.requestAddTileService(
                ComponentName(context, ScanTileService::class.java),
                context.getString(R.string.tile_scan_label),
                Icon.createWithResource(context, R.drawable.ic_tile_scan),
                context.mainExecutor,
            ) { result ->
                onResult(
                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                        result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED,
                )
            }
        }
    }
}
