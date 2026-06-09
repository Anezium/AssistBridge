import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Locale

plugins {
    id("com.android.application")
}

android {
    namespace = "com.anezium.assistbridge.phone"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.anezium.assistbridge.phone"
        minSdk = 31
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.1"

        manifestPlaceholders["debugRelayExported"] = "false"
    }

    buildTypes {
        debug {
            manifestPlaceholders["debugRelayExported"] = "true"
        }

        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            manifestPlaceholders["debugRelayExported"] = "false"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":cxrglobal-lib"))
    implementation("androidx.core:core:1.18.0")
    testImplementation("junit:junit:4.13.2")
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name
        val variantTaskName = variantName.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
        }
        val copyGlassesClient = tasks.register<BundleGlassesClientTask>("copy${variantTaskName}GlassesClient") {
            description = "Builds the $variantName glasses HUD app and bundles it as a generated phone asset."
            dependsOn(":glasses-app:assemble$variantTaskName")
            inputApk.set(rootProject.layout.projectDirectory.file(
                    "glasses-app/build/outputs/apk/$variantName/glasses-app-$variantName.apk"
            ))
            outputDirectory.set(layout.buildDirectory.dir("generated/assets/$variantName/assistBridgeGlasses"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(copyGlassesClient, BundleGlassesClientTask::outputDirectory)
    }
}

abstract class BundleGlassesClientTask : DefaultTask() {
    @get:InputFile
    abstract val inputApk: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copyApk() {
        val destination = File(outputDirectory.get().asFile, "assist-bridge-glasses.apk")
        destination.parentFile.mkdirs()
        inputApk.get().asFile.copyTo(destination, overwrite = true)
    }
}
