plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "de.civa"
version = "1.0.0"

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
}
