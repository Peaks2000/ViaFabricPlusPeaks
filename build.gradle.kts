import de.florianreuth.baseproject.*

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

subprojects {

    configureVVDependencies("api")

}

project.property("updating_minecraft").toString().toBoolean().let {
    configureTestTasks(it)
    if (it) {
        increaseVisibleBuildErrors()
    }
}

val jij = configureApiJij()

configureVVDependencies("jij")

dependencies {
    jij(project(":viafabricplus-api")) {
        exclude("net.fabricmc", "fabric-loader")
    }
    jij(project(":viafabricplus-visuals")) {
        exclude("net.fabricmc", "fabric-loader")
    }

    jij(fabricApi.module("fabric-api-base", fabricApiVersion))
    jij(fabricApi.module("fabric-resource-loader-v1", fabricApiVersion))
    jij(fabricApi.module("fabric-resource-loader-v0", fabricApiVersion))
    jij(fabricApi.module("fabric-networking-api-v1", fabricApiVersion))
    jij(fabricApi.module("fabric-command-api-v2", fabricApiVersion))
    jij(fabricApi.module("fabric-lifecycle-events-v1", fabricApiVersion))
    jij(fabricApi.module("fabric-particles-v1", fabricApiVersion))
    jij(fabricApi.module("fabric-registry-sync-v0", fabricApiVersion))

    jij("net.lenni0451:Reflect:1.6.4")
    jij("de.florianreuth:classic4j:2.3.0")
    configureBedrockDependencies()

    testImplementation("net.fabricmc:fabric-loader-junit:${property("fabric_loader_version")}")
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

fun configureBedrockDependencies() {
    val webrtcVersion = "1.0.4-m152"

    dependencies {
        jij("net.raphimc:MinecraftAuth:5.0.1") {
            exclude(group = "com.google.code.gson", module = "gson")
        }
        jij("dev.kastle.netty:netty-transport-raknet:1.7.3") {
            exclude(group = "io.netty")
        }
        jij("dev.kastle.netty:netty-transport-nethernet:1.7.3") {
            exclude(group = "io.netty")
            exclude(group = "org.bitbucket.b_c", module = "jose4j")
            exclude(group = "dev.kastle.webrtc", module = "webrtc-java")
        }
        jij("dev.kastle.webrtc:webrtc-java-m152test:$webrtcVersion")
        jij("dev.kastle.webrtc:webrtc-java-m152test:$webrtcVersion:linux-x86_64")
        jij("dev.kastle.webrtc:webrtc-java-m152test:$webrtcVersion:macos-aarch64")
    }
}

fun Project.configureVVDependencies(configuration: String) {
    dependencies {
        configuration("com.viaversion:viaversion-common:5.11.1-SNAPSHOT")
        configuration("com.viaversion:viabackwards-common:5.11.1-SNAPSHOT")
        configuration("com.viaversion:viaaprilfools-common:4.2.2")
        configuration("net.raphimc:ViaLegacy:3.0.16")
        configuration("net.raphimc:ViaBedrock") {
            version {
                branch = "peaks/1.26.40-fixes"
            }
            exclude(group = "com.mojang", module = "brigadier")
            exclude(group = "at.yawk.lz4", module = "lz4-java")
            exclude(group = "io.netty")
        }
    }
}
