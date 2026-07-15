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

    jij(fabricApi.module("fabric-api-base", fabricApiVersion))
    jij(fabricApi.module("fabric-resource-loader-v1", fabricApiVersion))
    jij(fabricApi.module("fabric-resource-loader-v0", fabricApiVersion))
    jij(fabricApi.module("fabric-networking-api-v1", fabricApiVersion))
    jij(fabricApi.module("fabric-command-api-v2", fabricApiVersion))
    jij(fabricApi.module("fabric-lifecycle-events-v1", fabricApiVersion))
    jij(fabricApi.module("fabric-particles-v1", fabricApiVersion))
    jij(fabricApi.module("fabric-registry-sync-v0", fabricApiVersion))

    jij("net.lenni0451:Reflect:1.6.3")
    jij("de.florianreuth:classic4j:2.3.0")

    testImplementation("net.fabricmc:fabric-loader-junit:${property("fabric_loader_version")}")
    compileOnly("com.terraformersmc:modmenu:20.0.0-beta.2")
}

includeTransitiveJijDependencies()

fun Project.configureVVDependencies(configuration: String) {
    dependencies {
        configuration("com.viaversion:viaversion-common:5.11.1-SNAPSHOT")
        configuration("com.viaversion:viabackwards-common:5.11.1-SNAPSHOT")
        configuration("com.viaversion:viaaprilfools-common:4.2.2")
        configuration("net.raphimc:ViaLegacy:3.0.16")
    }
}
