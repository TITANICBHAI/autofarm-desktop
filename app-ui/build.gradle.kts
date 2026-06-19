import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":automation-engine"))
    implementation(project(":mail-client"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "AutoFarm"
            packageVersion = "1.0.0"
            description = "Browser automation step runner"
            vendor = "AutoFarm"

            windows {
                menuGroup = "AutoFarm"
                upgradeUuid = "61DAB5E2-F9A2-4B89-9C98-71A2F3C4B9D1"
            }
            linux {
                // Icon is generated programmatically at runtime via AppIcon.kt
            }
        }
    }
}
