import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

val gdxVersion: String by project

// The upload key. It lives outside git: either in keystore.properties at the project root
// (storeFile, storePassword, keyAlias, keyPassword) or in the STAREATER_* environment variables.
// Without either, a release build is simply left unsigned, which is what CI wants.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun uploadSecret(name: String, env: String): String? = keystoreProps.getProperty(name) ?: System.getenv(env)

// libGDX ships its native code as jars; these get unpacked into libs/<abi>/ before packaging.
val natives: Configuration by configurations.creating

android {
    namespace = "io.github.allwise.stareater.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.allwise.stareater"
        minSdk = 24
        // Play wants new apps and updates on a recent target. 36 turns on the predictive back
        // gesture, which would swallow the back button the game catches: see the manifest.
        targetSdk = 36
        // Play rejects an upload whose versionCode it has seen before: raise it in gradle.properties.
        versionCode = (project.findProperty("appVersionCode") as String).toInt()
        versionName = project.version.toString()
    }

    sourceSets["main"].apply {
        assets.srcDir(rootProject.file("assets"))
        jniLibs.srcDir("libs")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    signingConfigs {
        val store = uploadSecret("storeFile", "STAREATER_KEYSTORE")
        if (store != null) {
            create("release") {
                storeFile = rootProject.file(store)
                storePassword = uploadSecret("storePassword", "STAREATER_KEYSTORE_PASSWORD")
                keyAlias = uploadSecret("keyAlias", "STAREATER_KEY_ALIAS")
                keyPassword = uploadSecret("keyPassword", "STAREATER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Left off on purpose: libGDX reaches for things by reflection, and a shrinker that
            // has not been tested against a real phone is a way to ship a game that will not start.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

// A release bundle without the upload key is useless to Play, so building one by hand without it is
// an error, not a quiet unsigned file. (CI sets CI=true and builds unsigned bundles on purpose.)
gradle.taskGraph.whenReady {
    if (!hasTask(":android:bundleRelease") && !hasTask(":android:assembleRelease")) return@whenReady
    val release = android.signingConfigs.findByName("release")
    val props = rootProject.file("keystore.properties")
    if (release == null) {
        if (System.getenv("CI") != null) return@whenReady
        throw GradleException(
            "No upload key found, so the release build would come out UNSIGNED and Play would refuse it.\n" +
                "  Looked for: ${props.absolutePath} (${if (props.exists()) "exists, but has no storeFile line" else "does not exist"})\n" +
                "  and the STAREATER_KEYSTORE environment variable (not set).\n" +
                "  keystore.properties needs: storeFile, storePassword, keyAlias, keyPassword. On Windows, check the file is not really called keystore.properties.txt."
        )
    }
    val store = release.storeFile
    if (store == null || !store.exists()) {
        throw GradleException("The upload keystore was not found at ${store?.absolutePath}. In keystore.properties, storeFile is relative to the project root; use forward slashes (C:/keys/upload.jks).")
    }
    logger.lifecycle("Release signing: ${store.absolutePath} (alias ${release.keyAlias})")
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    // The full-game unlock. Play wants a recent major version of this on every new upload.
    implementation("com.android.billingclient:billing:8.0.0")

    for (abi in listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")) {
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$abi")
        natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-$abi")
    }
}

abstract class CopyAndroidNatives @Inject constructor(
    private val archives: ArchiveOperations,
    private val fs: FileSystemOperations,
) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val jars: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val libsDir: DirectoryProperty

    @TaskAction
    fun unpack() {
        jars.files.forEach { jar ->
            // e.g. gdx-platform-1.14.2-natives-arm64-v8a.jar -> libs/arm64-v8a/
            val abi = jar.name.substringAfter("natives-").removeSuffix(".jar")
            fs.copy {
                from(archives.zipTree(jar))
                into(libsDir.dir(abi))
                include("*.so")
            }
        }
    }
}

val copyAndroidNatives by tasks.registering(CopyAndroidNatives::class) {
    jars.from(natives)
    libsDir.set(layout.projectDirectory.dir("libs"))
}

tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn(copyAndroidNatives)
}

// Keep the latest debug APK in the project root for easy transfer to a phone.
val copyDebugApk by tasks.registering {
    val apk = layout.buildDirectory.file("outputs/apk/debug/android-debug.apk")
    val target = rootProject.layout.projectDirectory.file("StarEater-debug.apk")
    inputs.file(apk)
    outputs.file(target)
    doLast {
        apk.get().asFile.copyTo(target.asFile, overwrite = true)
    }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(copyDebugApk)
}
