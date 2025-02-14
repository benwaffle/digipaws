package nethical.digipaws.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityEvent
import nethical.digipaws.blockers.ViewBlocker
import nethical.digipaws.utils.UsageStatOverlayManager
import java.util.concurrent.TimeUnit

class UsageTrackingService : AccessibilityService() {

    private var screenOnTime: Long = 0
    private var accumulatedTime: Long = 0
    private var isScreenOn = false
    private var lastScreenOffTime: Long = 0

    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private val usageStatOverlayManager by lazy { UsageStatOverlayManager(this) }

    private var userYSwipeEventCounter: Long =
        0 // basically tracks an estimate of how many pixels did the user scroll

    companion object {
        private const val UPDATE_INTERVAL = 1000L // 1 second
        private const val TAG = "ScreenTimeTracking"

        private val USER_Y_SWIPE_THRESHOLD: Long = 2

    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> handleScreenOn()
                Intent.ACTION_SCREEN_OFF -> handleScreenOff()
            }
        }
    }

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes =
                AccessibilityEvent.TYPE_VIEW_SCROLLED or AccessibilityEvent.TYPE_VIEW_CLICKED or AccessibilityEvent.TYPE_GESTURE_DETECTION_START or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)

        // Initialize if screen is already on
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (powerManager.isInteractive) {
            handleScreenOn()
        }

        usageStatOverlayManager.startDisplaying()
    }

    private fun handleScreenOn() {
        isScreenOn = true
        screenOnTime = System.currentTimeMillis()
        lastScreenOffTime = 0 // Reset screen off time
        startTimeTracking()
        Log.d(TAG, "Screen ON - Continuing from: ${formatElapsedTime(accumulatedTime)}")
    }

    private fun handleScreenOff() {
        isScreenOn = false
        lastScreenOffTime = System.currentTimeMillis()
        updateAccumulatedTime()
        stopTimeTracking()
        Log.d(TAG, "Screen OFF - Current accumulated: ${formatElapsedTime(accumulatedTime)}")
    }

    private fun startTimeTracking() {
        stopTimeTracking() // Clear any existing tracking

        updateRunnable = object : Runnable {
            override fun run() {
                if (isScreenOn) {
                    val currentTime = System.currentTimeMillis()
                    val totalTime = accumulatedTime + (currentTime - screenOnTime)
                    usageStatOverlayManager.binding?.timeElapsedTxt?.text =
                        formatElapsedTime(totalTime)
                    handler.postDelayed(this, UPDATE_INTERVAL)
                }
            }
        }
        handler.post(updateRunnable!!)
    }

    private fun stopTimeTracking() {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = null
    }

    private fun updateAccumulatedTime() {
        if (isScreenOn) {
            val currentTime = System.currentTimeMillis()
            accumulatedTime += (currentTime - screenOnTime)
            screenOnTime = currentTime
        }
    }

    @SuppressLint("DefaultLocale")
    private fun formatElapsedTime(milliseconds: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60

        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }


    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        Log.d(
            "youtube",
            event?.className.toString() + " " + event?.packageName + " " + event?.eventType.toString()
        )

        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            if (event.source?.className == "androidx.viewpager.widget.ViewPager") {
                logToReelCounter()
            } else if (event.packageName == "com.google.android.youtube" && event.source?.className == "android.support.v7.widget.RecyclerView") {
                val reelview = ViewBlocker.findElementById(
                    rootInActiveWindow,
                    "com.google.android.youtube:id/reel_recycler"
                )
                if (reelview != null) {
                    logToReelCounter()
                } else {
                    usageStatOverlayManager.binding?.reelCounter?.visibility = View.GONE
                }
            } else {
                usageStatOverlayManager.binding?.reelCounter?.visibility = View.GONE
            }
        }
    }

    private fun logToReelCounter() {
        userYSwipeEventCounter++
        if (userYSwipeEventCounter > USER_Y_SWIPE_THRESHOLD) {
            userYSwipeEventCounter = 0
            usageStatOverlayManager.binding?.reelCounter?.visibility = View.VISIBLE
            usageStatOverlayManager.incrementReelScrollCount()
        }
    }

    override fun onInterrupt() {
        stopTimeTracking()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        stopTimeTracking()
    }
}
