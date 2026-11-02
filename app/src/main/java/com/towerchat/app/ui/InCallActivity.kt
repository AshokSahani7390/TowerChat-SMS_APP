package com.towerchat.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.towerchat.app.BuildConfig
import com.towerchat.app.telephony.CallRepository
import com.towerchat.app.ui.screens.InCallScreen
import com.towerchat.app.ui.theme.TowerchatTheme

class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Option A: Apply FLAG_SECURE on release builds to protect in-call privacy
        if (!BuildConfig.DEBUG) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        // Keep screen on during call and allow display over lock screen
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContent {
            TowerchatTheme {
                val currentCalls by CallRepository.currentCalls.collectAsState()

                // Automatically close in-call screen when all calls are terminated
                LaunchedEffect(currentCalls) {
                    if (currentCalls.isEmpty()) {
                        finish()
                    }
                }

                InCallScreen(
                    onAddCall = {
                        val addCallIntent = Intent(this@InCallActivity, MainActivity::class.java).apply {
                            action = Intent.ACTION_DIAL
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(addCallIntent)
                    }
                )
            }
        }
    }
}
