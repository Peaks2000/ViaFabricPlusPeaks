dependencies {
    compileOnly("com.viaversion:viaversion-api:5.11.1-SNAPSHOT")
}

tasks {
    runClient {
        enabled = false
    }
}
