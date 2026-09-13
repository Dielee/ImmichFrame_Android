package com.immichframe.immichframe

import android.content.Context
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream

class HardwareDisplayManager(
    context: Context,
    private val motionPath: String = "/dev/motion0",
    private var timeoutMillis: Long = 5 * 60 * 1000L,
    private var isEnabled: Boolean = true,
    private val onSleepRequest: () -> Unit,
    private val onWakeRequest: () -> Unit,
    private val canWake: () -> Boolean = { true }
) {
    private val tag = "HardwareDisplayManager"
    private var managerScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listeningJob: Job? = null
    private var sleepJob: Job? = null
    private var isDisplaySleeping = false

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val cpuWakeLock = powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "immichframe:motionCpuWake"
    )

    fun start() {
        stopListening()

        if (!isEnabled) {
            wakeDisplay()
            return
        }

        listeningJob = managerScope.launch {
            val motionFile = File(motionPath)
            val buffer = ByteArray(1)

            resetSleepTimer()

            while (isActive) {
                try {
                    if (motionFile.exists()) {
                        FileInputStream(motionFile).use { stream ->
                            if (stream.read(buffer) == 1 && buffer[0].toInt() == 1) {
                                if (canWake()) {
                                    if (isDisplaySleeping) {
                                        Log.d(tag, "Motion detected -> waking display")
                                        withContext(Dispatchers.Main) {
                                            wakeDisplay()
                                        }
                                    }
                                    resetSleepTimer()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Error reading from $motionPath: ${e.message}")
                }
                delay(300)
            }
        }
    }

    fun updateConfig(enabled: Boolean, newTimeoutMillis: Long) {
        this.isEnabled = enabled
        this.timeoutMillis = newTimeoutMillis
        Log.d(tag, "Config updated: enabled=$enabled, timeout=${newTimeoutMillis / 1000}s")

        if (!enabled) {
            stopListening()
            wakeDisplay()
        } else {
            start()
        }
    }

    fun resetSleepTimer() {
        sleepJob?.cancel()
        if (!isEnabled) return

        sleepJob = managerScope.launch {
            delay(timeoutMillis)
            if (!isDisplaySleeping) {
                Log.d(tag, "Inactivity timeout reached -> sleeping display")
                withContext(Dispatchers.Main) {
                    sleepDisplay()
                }
            }
        }
    }

    fun sleepDisplay() {
        if (isDisplaySleeping) return
        isDisplaySleeping = true
        sleepJob?.cancel()

        try {
            if (!cpuWakeLock.isHeld) {
                cpuWakeLock.acquire()
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to acquire CPU wake lock: ${e.message}")
        }

        onSleepRequest()
    }

    fun wakeDisplay() {
        isDisplaySleeping = false

        try {
            if (cpuWakeLock.isHeld) {
                cpuWakeLock.release()
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to release CPU wake lock: ${e.message}")
        }

        onWakeRequest()
        resetSleepTimer()
    }

    private fun stopListening() {
        listeningJob?.cancel()
        listeningJob = null
        sleepJob?.cancel()
        sleepJob = null
    }

    fun stop() {
        stopListening()
        try {
            if (cpuWakeLock.isHeld) {
                cpuWakeLock.release()
            }
        } catch (_: Exception) { }
        managerScope.cancel()
        managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }
}