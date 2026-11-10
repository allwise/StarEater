package io.github.allwise.stareater.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import io.github.allwise.stareater.DevOptions
import io.github.allwise.stareater.StarEaterGame

/** Desktop entry point. */
object Lwjgl3Launcher {
    @JvmStatic
    fun main(args: Array<String>) {
        // Dev switches: -Dstareater.demo="screen=play;level=7;bot=1" (or `./gradlew lwjgl3:run -Pdemo=...`).
        val dev = System.getProperty("stareater.demo")?.takeIf { it.isNotBlank() }?.let { DevOptions(it) }
        val config = Lwjgl3ApplicationConfiguration().apply {
            setTitle("Star Eater")
            setWindowedMode(1280, 720)
            setWindowSizeLimits(640, 360, -1, -1)
            useVsync(true)
            setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate.coerceAtLeast(60))
            setPreferencesConfig(".stareater", com.badlogic.gdx.Files.FileType.External)
        }
        Lwjgl3Application(StarEaterGame(dev), config)
    }
}
