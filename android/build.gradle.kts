import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
}

val gdxVersion: String by project

// libGDX ships its native code as jars; these get unpacked into libs/<abi>/ before packaging.
val natives: Configuration by configurations.creating

android {
    namespace = "io.github.allwise.stareater.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.allwise.stareater"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
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

    buildTypes {
        release {
            isMinifyEnabled = false
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
