package net.backslasher.dndtoggle

import android.app.NotificationManager
import android.app.PendingIntent

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
class DnDTileService : TileService() {

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        // Check if the app has permission to modify Do Not Disturb (DND) settings
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            // Do nothing on short click if permission is not granted
            return
        }

        // Launch a coroutine because DataStore access is asynchronous
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            try {
                // 1. Fetch current preferences from DataStore
                val preferences = dataStore.data.first()
                val allowedStatesKey = stringSetPreferencesKey("allowed-states")
                val allowedStates = preferences[allowedStatesKey] ?: emptySet()

                val currentFilter = notificationManager.currentInterruptionFilter

                // 2. Logic to cycle through notification states
                // We use 'when' for better readability compared to multiple 'if-else'
                when {
                    currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL -> {
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    }

                    // Check if Priority is active AND if the user allowed Alarms in settings
                    (currentFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY && allowedStates.contains("filter-alarms")) -> {
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                    }

                    // Check if Alarms are active AND if the user allowed Total Silence (None)
                    (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS && allowedStates.contains("filter-none")) -> {
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                    }

                    // Default case: reset to 'All Notifications'
                    else -> {
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    }
                }

                // 3. Update the UI or Quick Settings Tile to reflect the new state
                updateTile()

            } catch (e: Exception) {
                // Handle potential disk I/O errors
                e.printStackTrace()
            }
        }
    }

    

    private fun updateTile() {
        val tile = qsTile
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = getString(R.string.dnd_permission);
            tile.icon = Icon.createWithResource(this, R.drawable.ic_dnd_off)
            tile.updateTile()
            return
        }

        when (notificationManager.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.dnd_off);
                tile.icon = Icon.createWithResource(this, R.drawable.ic_dnd_off)
            }
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = getString(R.string.dnd_priority);
                tile.icon = Icon.createWithResource(this, R.drawable.ic_dnd_on)
            }
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = getString(R.string.dnd_alarms);
                tile.icon = Icon.createWithResource(this, R.drawable.ic_dnd_on)
            }
            NotificationManager.INTERRUPTION_FILTER_NONE -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = getString(R.string.dnd_none);
                tile.icon = Icon.createWithResource(this, R.drawable.ic_dnd_on)
            }
            NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.label = getString(R.string.dnd_off);
                tile.icon = Icon.createWithResource(this, R.drawable.ic_dnd_off)
            }
        }
        tile.updateTile()
    }
}
