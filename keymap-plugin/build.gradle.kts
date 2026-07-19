plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "de.civa"
version = "1.2.0"

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
