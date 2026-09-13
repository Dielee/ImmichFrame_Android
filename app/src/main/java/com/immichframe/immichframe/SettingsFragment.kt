package com.immichframe.immichframe

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import java.util.Calendar

class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_view, rootKey)

        val context = requireContext()
        dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = FrameDeviceAdminReceiver.componentName(context)

        // 1. Connection & Display Options
        val useWebViewPref = findPreference<SwitchPreferenceCompat>("useWebView")
        val blurredBackgroundPref = findPreference<SwitchPreferenceCompat>("blurredBackground")
        val showCurrentDatePref = findPreference<SwitchPreferenceCompat>("showCurrentDate")

        fun updateDisplayOptions(isWebView: Boolean) {
            blurredBackgroundPref?.isEnabled = !isWebView
            showCurrentDatePref?.isEnabled = !isWebView
        }

        useWebViewPref?.let { pref ->
            updateDisplayOptions(pref.isChecked)
            pref.setOnPreferenceChangeListener { _, newValue ->
                updateDisplayOptions(newValue as Boolean)
                true
            }
        }

        // 2. Auth Secret Masking & Summary
        val authSecretPref = findPreference<EditTextPreference>("authSecret")
        authSecretPref?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        authSecretPref?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { preference ->
            val text = preference.text
            if (text.isNullOrEmpty()) {
                "Not set"
            } else {
                "••••••••"
            }
        }

        // 3. Settings Lock Warning
        findPreference<SwitchPreferenceCompat>("settingsLock")?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                Toast.makeText(
                    context,
                    "Settings lock enabled.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            true
        }

        // 4. Active Times Schedule
        findPreference<SwitchPreferenceCompat>("activeTimes")?.setOnPreferenceChangeListener { _, _ ->
            view?.post { updateScheduleSummary() }
            true
        }

        findPreference<Preference>("active_schedule_edit")?.setOnPreferenceClickListener {
            try {
                startActivity(Intent(context, ActiveScheduleActivity::class.java))
            } catch (_: Exception) { }
            true
        }

        // 5. System Settings & Close Button
        findPreference<Preference>("androidSettings")?.setOnPreferenceClickListener {
            startActivity(Intent(Settings.ACTION_SETTINGS))
            true
        }

        findPreference<Preference>("closeSettings")?.setOnPreferenceClickListener {
            requireActivity().setResult(Activity.RESULT_OK)
            requireActivity().finish()
            true
        }

        // 6. Device Admin Permissions (Motion Sensor & Active Times)
        findPreference<Preference>("motion_admin_permission")?.setOnPreferenceClickListener {
            requestDeviceAdmin()
            true
        }

        findPreference<Preference>("active_schedule_admin")?.setOnPreferenceClickListener {
            requestDeviceAdmin()
            true
        }

        updateScheduleSummary()
    }

    override fun onResume() {
        super.onResume()
        updateAdminPreferences()
        updateScheduleSummary()
    }

    private fun requestDeviceAdmin() {
        if (dpm.isAdminActive(adminComponent)) {
            return
        }
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Allows ImmichFrame to turn off the screen and sleep the device when inactive."
            )
        }
        startActivity(intent)
    }

    private fun updateAdminPreferences() {
        val isAdmin = dpm.isAdminActive(adminComponent)

        findPreference<Preference>("motion_admin_permission")?.apply {
            if (isAdmin) {
                summary = "Permission granted ✓"
                isEnabled = false
            } else {
                summary = "Allow the frame to turn off the screen completely (Device Admin)"
                isEnabled = true
            }
        }

        findPreference<Preference>("active_schedule_admin")?.apply {
            if (isAdmin) {
                summary = "Permission granted ✓"
                isEnabled = false
            } else {
                summary = "Allow the frame to turn off the screen and sleep the device during inactive hours"
                isEnabled = true
            }
        }
    }

    private fun updateScheduleSummary() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val raw = prefs.getString("activeSchedule", null)
        val schedule = Helpers.parseActiveSchedule(raw)
        val summary = formatScheduleSummary(schedule)
        findPreference<Preference>("active_schedule_edit")?.let { pref ->
            pref.summary = if (summary.isNotBlank()) summary else "Per-weekday on/off times"
        }
    }

    private fun formatScheduleSummary(schedule: Helpers.ActiveSchedule?): String {
        if (schedule == null) return ""
        val cal = Calendar.getInstance()
        val next = Helpers.nextActiveStart(schedule, cal)
        val isActive = Helpers.isActiveNow(schedule, cal)

        return if (isActive) {
            "Currently active"
        } else if (next != null) {
            val hour = String.format("%02d", next.get(Calendar.HOUR_OF_DAY))
            val minute = String.format("%02d", next.get(Calendar.MINUTE))
            "Inactive • Next on at $hour:$minute"
        } else {
            "Schedule configured"
        }
    }
}