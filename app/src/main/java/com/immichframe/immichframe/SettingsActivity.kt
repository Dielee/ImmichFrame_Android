package com.immichframe.immichframe

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setResult(RESULT_OK)
                finish()
            }
        })

        val layoutId = resources.getIdentifier("settings_activity", "layout", packageName)
        if (layoutId != 0) {
            setContentView(layoutId)
        }

        if (savedInstanceState == null) {
            val containerId = resources.getIdentifier("settings", "id", packageName).let {
                if (it != 0) it else android.R.id.content
            }
            supportFragmentManager
                .beginTransaction()
                .replace(containerId, SettingsFragment())
                .commit()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            setResult(RESULT_OK)
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private lateinit var dpm: DevicePolicyManager
        private lateinit var adminComponent: ComponentName

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.settings_view, rootKey)

            val context = requireContext()
            dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            adminComponent = FrameDeviceAdminReceiver.componentName(context)

            findPreference<Preference>("androidSettings")?.setOnPreferenceClickListener {
                startActivity(Intent(Settings.ACTION_SETTINGS))
                true
            }

            findPreference<Preference>("active_schedule_edit")?.setOnPreferenceClickListener {
                try {
                    val intent = Intent(context, Class.forName("com.immichframe.immichframe.ActiveScheduleActivity"))
                    startActivity(intent)
                } catch (_: Exception) { }
                true
            }

            findPreference<Preference>("closeSettings")?.setOnPreferenceClickListener {
                requireActivity().setResult(RESULT_OK)
                requireActivity().finish()
                true
            }

            findPreference<Preference>("motion_admin_permission")?.setOnPreferenceClickListener {
                requestDeviceAdmin()
                true
            }

            findPreference<Preference>("active_schedule_admin")?.setOnPreferenceClickListener {
                requestDeviceAdmin()
                true
            }
        }

        override fun onResume() {
            super.onResume()
            updateAdminPreferences()
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

            val motionAdminPref = findPreference<Preference>("motion_admin_permission")
            val activeAdminPref = findPreference<Preference>("active_schedule_admin")

            if (isAdmin) {
                motionAdminPref?.summary = "Permission granted ✓"
                motionAdminPref?.isEnabled = false

                activeAdminPref?.summary = "Permission granted ✓"
                activeAdminPref?.isEnabled = false
            } else {
                motionAdminPref?.summary = "Allow the frame to turn off the screen completely (Device Admin)"
                motionAdminPref?.isEnabled = true

                activeAdminPref?.summary = "Allow the frame to turn off the screen and sleep the device during inactive hours"
                activeAdminPref?.isEnabled = true
            }
        }
    }
}