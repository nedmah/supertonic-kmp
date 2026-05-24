import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    `maven-publish`
}

group = "io.github.nedmah"
version = "0.1.0"

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SupertonicKMP"
            isStatic = true
        }
    }
    
    androidLibrary {
       namespace = "com.nedmah.supertonic_kmp"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.onnxruntime.android)
        }
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines)
            implementation(libs.kotlinx.serialization)
            implementation(libs.okio)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/nedmah/supertonic-kmp")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .get()
                password = providers.gradleProperty("gpr.token")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .get()
            }
        }
    }
}

// Copy commonMain resources into the iOS framework bundle
val copyResourcesToFramework by tasks.registering {
    notCompatibleWithConfigurationCache("Copies resources to iOS framework")
    val resourcesDir = file("src/commonMain/resources")
    val frameworks = listOf(
        "build/bin/iosArm64/debugFramework/SupertonicKMP.framework",
        "build/bin/iosArm64/releaseFramework/SupertonicKMP.framework",
        "build/bin/iosSimulatorArm64/debugFramework/SupertonicKMP.framework",
        "build/bin/iosSimulatorArm64/releaseFramework/SupertonicKMP.framework",
    )
    doLast {
        frameworks.forEach { frameworkPath ->
            val dest = file("$frameworkPath/supertonic")
            if (file(frameworkPath).exists()) {
                copy {
                    from(resourcesDir)
                    into(file(frameworkPath))
                }
            }
        }
    }
}

tasks.matching { it.name.contains("linkDebugFrameworkIos") || it.name.contains("linkReleaseFrameworkIos") }.configureEach {
    finalizedBy(copyResourcesToFramework)
}
