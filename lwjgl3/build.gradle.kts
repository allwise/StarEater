import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    application
    kotlin("jvm")
}

val gdxVersion: String by project
val assetsDir = rootProject.file("assets")

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

sourceSets {
    main { resources.srcDir(assetsDir) }
}

application {
    mainClass.set("io.github.allwise.stareater.lwjgl3.Lwjgl3Launcher")
}

tasks.named<JavaExec>("run") {
    workingDir = assetsDir
    // macOS needs GLFW on the first thread.
    if (System.getProperty("os.name").lowercase().contains("mac")) jvmArgs("-XstartOnFirstThread")
    // Dev switches, e.g. -Pdemo="screen=play;level=7;bot=1"
    (findProperty("demo") as String?)?.let { systemProperty("stareater.demo", it) }
}

// `./gradlew lwjgl3:dist` -> one runnable jar in lwjgl3/build/libs
tasks.register<Jar>("dist") {
    group = "distribution"
    description = "Builds a single runnable desktop jar."
    archiveBaseName.set("StarEater")
    archiveClassifier.set("desktop")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = application.mainClass.get() }
    dependsOn(configurations.runtimeClasspath)
    from(sourceSets.main.get().output)
    from({ configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/INDEX.LIST")
    }
}
