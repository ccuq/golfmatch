import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("app.cash.sqldelight")
}

kotlin {

    // Cible JVM : sert aux tests (et plus tard au module Android).
    jvm()

    // Cibles iOS : compilées uniquement sur macOS.
    val xcf = XCFramework("Shared")
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach {
        it.binaries.framework {
            baseName = "Shared"
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
            api("app.cash.sqldelight:runtime:2.4.1")
        }
        jvmMain.dependencies {
            implementation("app.cash.sqldelight:sqlite-driver:2.4.1")
        }
        iosMain.dependencies {
            implementation("app.cash.sqldelight:native-driver:2.4.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

sqldelight {
    databases {
        create("GolfDatabase") {
            packageName.set("org.golfmatch.db")
        }
    }
}
