package io.github.allwise.stareater.android

import android.os.Bundle
import android.view.WindowManager
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import io.github.allwise.stareater.StarEaterGame

/** Android entry point. All the game code lives in :core; this only boots it. */
class AndroidLauncher : AndroidApplication() {
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
        initialize(StarEaterGame(), config)
    }
}
