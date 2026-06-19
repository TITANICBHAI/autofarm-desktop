plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core"))
    implementation("com.sun.mail:jakarta.mail:2.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
