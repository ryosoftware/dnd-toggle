package net.backslasher.dndtoggle

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.content.ComponentName
import android.service.quicksettings.TileService
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var notificationManager: NotificationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val permissionButton = findViewById<Button>(R.id.permission_button)

        permissionButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivity(intent)
        }

        val checkAlarms = findViewById<CheckBox>(R.id.allow_dnd_alarms)
        val checkNone = findViewById<CheckBox>(R.id.allow_dnd_none)

        lifecycleScope.launch {
            try {
                // Read the first emission from DataStore (the current values)
                val preferences = dataStore.data.first()
                val allowedStatesKey = stringSetPreferencesKey("allowed-states")
                val allowedStates = preferences[allowedStatesKey] ?: emptySet()

                // Update UI based on the Set contents
                checkAlarms.isChecked = allowedStates.contains("filter-alarms")
                checkNone.isChecked = allowedStates.contains("filter-none")

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        checkAlarms.setOnCheckedChangeListener { _, isChecked ->
            savePreference("filter-alarms", isChecked)
        }

        checkNone.setOnCheckedChangeListener { _, isChecked ->
            savePreference("filter-none", isChecked)
        }

        updateUi()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
        TileService.requestListeningState(this, ComponentName(this, DnDTileService::class.java))
    }

    private fun updateUi() {
        val permissionStatusText = findViewById<TextView>(R.id.permission_status_text)
        if (notificationManager.isNotificationPolicyAccessGranted) {
            permissionStatusText.text = getString(R.string.dnd_permission_granted);
        } else {
            permissionStatusText.text = getString(R.string.dnd_permission_not_granted);
        }
    }

    // Helper function to update the Set in DataStore
    private fun savePreference(value: String, add: Boolean) {
        val allowedStatesKey = stringSetPreferencesKey("allowed-states")
        lifecycleScope.launch {
            dataStore.edit { settings ->
                val currentSet = settings[allowedStatesKey]?.toMutableSet() ?: mutableSetOf()
                if (add) {
                    currentSet.add(value)
                } else {
                    currentSet.remove(value)
                }
                settings[allowedStatesKey] = currentSet
            }
        }
    }
}
