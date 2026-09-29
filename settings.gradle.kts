pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
    }
}

plugins {
    id("base.settings")
    id("base.fabric_settings")
}

dependencyResolutionManagement {
    repositories {
        maven(rootDir.resolve("vendor/maven"))
        maven("https://maven.lenni0451.net/everything")
        maven("https://jitpack.io") {
            content { includeGroup("com.github.oryxel1") }
        }
        maven("https://repo.viaversion.com")
        maven("https://maven.terraformersmc.com/releases")
        //mavenLocal() // Uncomment during Minecraft updates for preview VV/VB builds
    }
}

rootProject.name = "viafabricplus"

sourceControl {
    gitRepository(uri("https://github.com/Peaks2000/ViaBedrock.git")) {
        producesModule("net.raphimc:ViaBedrock")
    }
}

include("viafabricplus-api")
