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

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")

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
