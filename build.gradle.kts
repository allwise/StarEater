buildscript {
    val kotlinVersion: String by project
    val agpVersion: String by project
    val withAndroid = findProject(":android") != null

    repositories {
        mavenCentral()
        gradlePluginPortal()
        if (withAndroid) google()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        if (withAndroid) classpath("com.android.tools.build:gradle:$agpVersion")
    }
}

allprojects {
    group = "io.github.allwise.stareater"
    version = "0.1.0"

    repositories {
        mavenCentral()
        google()
    }
}
