plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":mail-client"))
    implementation("com.microsoft.playwright:playwright:1.44.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("org.jetbrains.exposed:exposed-core:0.52.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.52.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.52.0")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    // OpenCV for image template matching (bundles native .so/.dll)
    implementation("org.openpnp:opencv:4.9.0-0")
}
