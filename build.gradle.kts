import de.florianreuth.baseproject.core.unlockBuildErrors
import de.florianreuth.baseproject.integration.*
import de.florianreuth.baseproject.setupProject
import de.florianreuth.baseproject.setupViaPublishing

plugins {
    id("net.fabricmc.fabric-loom")
    id("de.florianreuth.baseproject")
}

allprojects {

    setupProject()
    setupFabric()
    setupViaPublishing()

    repositories {
        maven(rootProject.file("vendor/maven"))
        // Keep them in sync with docs/DEVELOPER_API.md
        maven("https://repo.viaversion.com")
        maven("https://maven.lenni0451.net/everything")
        maven("https://maven.terraformersmc.com/releases")
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.oryxel1")
            }
        }

        //mavenLocal() // Uncomment during Minecraft updates for preview VV/VB builds
    }

}

configureTest().also {
    // Uncomment during Minecraft updates to update data diff files
    tasks.test.get().enabled = false
}
unlockBuildErrors()

val shade = configureJarInJar()

dependencies {
    shade(project(":viafabricplus-api")) {
        exclude("net.fabricmc", "fabric-loader")
    }

    shade(fabricApi.module("fabric-api-base", fabricApiVersion))
    shade(fabricApi.module("fabric-resource-loader-v1", fabricApiVersion))
    shade(fabricApi.module("fabric-resource-loader-v0", fabricApiVersion))
    shade(fabricApi.module("fabric-networking-api-v1", fabricApiVersion))
    shade(fabricApi.module("fabric-command-api-v2", fabricApiVersion))
    shade(fabricApi.module("fabric-lifecycle-events-v1", fabricApiVersion))
    shade(fabricApi.module("fabric-particles-v1", fabricApiVersion))
    shade(fabricApi.module("fabric-registry-sync-v0", fabricApiVersion))

    shade("com.viaversion:viaversion-common:5.11.1-SNAPSHOT")
    shade("com.viaversion:viabackwards-common:5.11.1-SNAPSHOT")
    shade("com.viaversion:viaaprilfools-common:4.2.2")
    shade("net.raphimc:ViaLegacy:3.0.16")
    shade("net.lenni0451:Reflect:1.6.4")
    shade("de.florianreuth:classic4j:2.3.0")
    shade("net.raphimc:ViaBedrock") {
        version {
            branch = "peaks/1.26.40-fixes"
        }
        exclude(group = "com.mojang", module = "brigadier")
        exclude(group = "at.yawk.lz4", module = "lz4-java")
        exclude(group = "io.netty")
    }
    shade("net.raphimc:MinecraftAuth:5.0.1") {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    shade("dev.kastle.netty:netty-transport-raknet:1.7.3") {
        exclude(group = "io.netty")
    }
    shade("dev.kastle.netty:netty-transport-nethernet:1.7.3") {
        exclude(group = "io.netty")
        exclude(group = "org.bitbucket.b_c", module = "jose4j")
        exclude(group = "dev.kastle.webrtc", module = "webrtc-java")
    }
    shade("dev.kastle.webrtc:webrtc-java-m152test:1.0.4-m152")
    shade("dev.kastle.webrtc:webrtc-java-m152test:1.0.4-m152:linux-x86_64")
    shade("dev.kastle.webrtc:webrtc-java-m152test:1.0.4-m152:macos-aarch64")

    compileOnly("com.terraformersmc:modmenu:20.0.0-beta.2")
}

tasks.named<Jar>("jar") {
    from(rootProject.file("LICENSE")) {
        into("META-INF/licenses/viafabricplus")
    }
    from(rootProject.file("THIRD_PARTY_NOTICES.md")) {
        into("META-INF")
    }
    from(rootProject.file("vendor/maven/licenses")) {
        into("META-INF/licenses")
    }
}

includeTransitiveJijDependencies()
