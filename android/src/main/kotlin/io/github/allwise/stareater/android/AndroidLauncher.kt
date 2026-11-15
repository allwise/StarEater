package io.github.allwise.stareater.android

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import io.github.allwise.stareater.Platform
import io.github.allwise.stareater.StarEaterGame

/** Android entry point. All the game code lives in :core; this only boots it. */
class AndroidLauncher : AndroidApplication() {
    private val platform = object : Platform {
        /**
         * While a level is being played the screen stays whichever way up it is: tilt steering leans
         * the phone about, and that must not flip the picture. Everywhere else it follows the phone
         * between the two landscape ways round, as the manifest asks.
         */
        override fun lockOrientation(locked: Boolean) {
            runOnUiThread {
                requestedOrientation = if (locked) ActivityInfo.SCREEN_ORIENTATION_LOCKED else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            // Tilt steering reads the accelerometer.
            useAccelerometer = true
            useCompass = false
        }
        // Steering by tilt means nobody touches the screen for minutes on end: do not let it doze off.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        initialize(StarEaterGame(platform = platform), config)
    }
}
