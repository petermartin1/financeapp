import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(libs.koin.core)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.financeapp.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Finance App"
            // Overridable from CI (`-PappVersion=1.2.3`, derived from the git tag) so a
            // release's installers carry the tag's version; defaults for local builds.
            packageVersion = (findProperty("appVersion") as String?)?.takeIf { it.isNotBlank() } ?: "1.0.0"

            // JDK modules the trimmed jlink runtime must include. Without java.sql,
            // H2/Exposed fail at runtime with "java.sql.Driver". List from
            // `./gradlew :desktopApp:suggestRuntimeModules`.
            modules("java.instrument", "java.management", "java.naming", "java.sql", "jdk.unsupported")

            macOS {
                bundleID = "com.financeapp.desktop"
            }
        }
    }
}
