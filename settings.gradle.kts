rootProject.name = "StarEater"

include("core", "lwjgl3")

// The Android module needs the Android SDK. It is only wired in when one is configured,
// so the desktop build works on a machine that has nothing but a JDK.
val hasAndroidSdk = file("local.properties").exists() ||
    System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null
if (hasAndroidSdk) include("android")
