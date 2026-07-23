import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "de.civa"
version = "1.5.0"

tasks.withType<JavaCompile> {
    // platform 261+ runs on JBR 21; class files built with the local JDK 17 load fine there
    options.release = 17
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2026.1.4") { useInstaller = false }
    }
}

tasks.runIde {
    // sandbox only: dump the macOS system-shortcut table (the conflict-check basis) to idea.log
    jvmArgs("-Ddebug.system.shortcuts=true")
}

intellijPlatform {
    // resource-only plugin: nothing to index for searchable options
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
            // keymap-only plugin: no API surface, don't cap compatibility
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        // flags @ApiStatus.Internal / impl-package usage and deprecations against the target IDE.
        // Pinned explicitly (recommended() resolves empty for the very new 261 build) and via the
        // Maven repo (useInstaller = false) because the installer URL doesn't resolve for this version.
        ides {
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2026.1.4") {
                useInstaller = false
            }
        }
    }
}
