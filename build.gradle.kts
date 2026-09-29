plugins {
    id("base.java")
    id("base.fabric")
    id("configuration.transitive_jar_in_jar")
    id("via.maven_publish")
    id("base.junit")
    id("extra.unlock_build_errors")
}

// Comment during Minecraft updates to update data diff files
tasks.test {
    enabled = false
}

dependencies {
    jarInJar(projects.viafabricplusApi) {
        exclude("net.fabricmc", "fabric-loader")
    }

    jarInJar(platform(libs.fabric.api.bom))
    jarInJar(libs.fabric.api.base)
    jarInJar(libs.fabric.resource.loader.v1)
    jarInJar(libs.fabric.resource.loader.v0)
    jarInJar(libs.fabric.networking.api.v1)
    jarInJar(libs.fabric.command.api.v2)
    jarInJar(libs.fabric.lifecycle.events.v1)
    jarInJar(libs.fabric.particles.v1)
    jarInJar(libs.fabric.registry.sync.v0)

    jarInJar(libs.reflect)
    jarInJar(libs.classic4j)

    jarInJar("net.raphimc:ViaBedrock") {
        version {
            branch = "experiment/26.3"
        }
        exclude(group = "com.mojang", module = "brigadier")
        exclude(group = "at.yawk.lz4", module = "lz4-java")
        exclude(group = "io.netty")
    }
    jarInJar("net.raphimc:MinecraftAuth:5.0.1") {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    jarInJar("dev.kastle.netty:netty-transport-raknet:1.7.3") {
        exclude(group = "io.netty")
    }
    jarInJar("dev.kastle.netty:netty-transport-nethernet:1.7.3") {
        exclude(group = "io.netty")
        exclude(group = "org.bitbucket.b_c", module = "jose4j")
        exclude(group = "dev.kastle.webrtc", module = "webrtc-java")
    }
    jarInJar("dev.kastle.webrtc:webrtc-java-m152test:1.0.4-m152")
    jarInJar("dev.kastle.webrtc:webrtc-java-m152test:1.0.4-m152:linux-x86_64")
    jarInJar("dev.kastle.webrtc:webrtc-java-m152test:1.0.4-m152:macos-aarch64")

    compileOnly(libs.modmenu)
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

// Build both route artifacts from the same resolved dependency. A checked-in old
// runtime must never be shipped beside a newer maintained translator.
val maintainedRuntime = configurations.named("runtimeClasspath").get().incoming.artifactView {
    componentFilter { id ->
        when (id) {
            is ModuleComponentIdentifier -> id.group == "net.raphimc" && id.module == "ViaBedrock"
            is ProjectComponentIdentifier -> id.projectName == "ViaBedrock"
            else -> false
        }
    }
}.files
val isolatedRuntime = tasks.register<Copy>("prepareIsolatedBedrockRuntime") {
    from(maintainedRuntime)
    into(layout.buildDirectory.dir("generated/isolated-bedrock/viafabricplus/compatibility"))
    rename { "ViaBedrock-compatibility-1.26.52.jar" }
    doFirst { require(maintainedRuntime.files.size == 1) { "Expected exactly one maintained ViaBedrock artifact" } }
}
tasks.processResources {
    exclude("viafabricplus/compatibility/ViaBedrock-compatibility-1.26.40.jar")
    dependsOn(isolatedRuntime)
    from(layout.buildDirectory.dir("generated/isolated-bedrock"))
}
