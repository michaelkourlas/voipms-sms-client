import org.gradle.internal.extensions.stdlib.capitalized

plugins {
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.application")
    id("kotlin-android")
    id("net.kourlas.oss-licenses-plugin")

    // fdroid-remove-start
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    // fdroid-remove-end
}

android {
    compileSdk = 35
    defaultConfig {
        applicationId = "net.kourlas.voipms_sms"
        minSdk = 21
        targetSdk = 35
        versionCode = 149
        versionName = "0.6.28"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
        }
    }
    flavorDimensions += "version"
    flavorDimensions += "demo"
    productFlavors {
        // fdroid-remove-start
        create("primary") {
            dimension = "version"
            buildConfigField("boolean", "IS_FDROID", "false")
        }
        // fdroid-remove-end
        create("fdroid") {
            dimension = "version"
            versionNameSuffix = "-fdroid"
            buildConfigField("boolean", "IS_FDROID", "true")
        }
        create("full") {
            dimension = "demo"
            buildConfigField("boolean", "IS_DEMO", "false")
            buildConfigField("boolean", "IS_DEMO_SENDING", "false")
        }
        create("demoNotSending") {
            dimension = "demo"
            applicationId = "net.kourlas.voipms_sms.demo"
            buildConfigField("boolean", "IS_DEMO", "true")
            buildConfigField("boolean", "IS_DEMO_SENDING", "false")
            versionNameSuffix = "-demo"
        }
        create("demoSending") {
            dimension = "demo"
            applicationId = "net.kourlas.voipms_sms.demo"
            buildConfigField("boolean", "IS_DEMO", "true")
            buildConfigField("boolean", "IS_DEMO_SENDING", "true")
            versionNameSuffix = "-demo"
        }
    }
    androidComponents.beforeVariants {
        it.enable = run {
            val names = it.productFlavors.map { it ->
                it.second
            }
            val isDemo =
                names.contains("demoSending") || names.contains("demoNotSending")
            val isRelease = it.buildType == "release"
            val isPrimary = names.contains("primary")
            !isDemo || (!isPrimary && !isRelease)
        }
    }
    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_17)
        targetCompatibility(JavaVersion.VERSION_17)
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("main") {
        java.srcDir("src/main/kotlin")
    }
    // fdroid-remove-start
    sourceSets.getByName("primary") {
        java.srcDir("src/primary/kotlin")
    }
    // fdroid-remove-end
    sourceSets.getByName("fdroid") {
        java.srcDir("src/fdroid/kotlin")
    }
    lint {
        abortOnError = false
    }
    namespace = "net.kourlas.voipms_sms"
}

dependencies {
    val roomVersion = "2.6.1"
    val moshiVersion = "1.15.2"

    // Kotlin libraries
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // Android support libraries
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.sharetarget:sharetarget:1.2.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.google.android.material:material:1.12.0")
    ksp("androidx.room:room-compiler:$roomVersion")

    // fdroid-remove-start

    // Firebase libraries
    "primaryImplementation"(platform("com.google.firebase:firebase-bom:33.8.0"))
    "primaryImplementation"("com.google.firebase:firebase-analytics-ktx")
    "primaryImplementation"("com.google.firebase:firebase-crashlytics-ktx")
    "primaryImplementation"("com.google.firebase:firebase-messaging-ktx")

    // fdroid-remove-end

    // Other third-party libraries
    implementation("com.squareup.moshi:moshi-adapters:$moshiVersion")
    implementation("com.squareup.moshi:moshi:$moshiVersion")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.mukeshsolanki:MarkdownView-Android:2.0.0")
    implementation("com.github.xabaras:RecyclerViewSwipeDecorator:1.3")
    implementation("me.saket:better-link-movement-method:2.2.0")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:$moshiVersion")
}

tasks.register<Delete>("cleanAssets") {
    delete("src/main/assets")
}

tasks.register<Copy>("copyToAssets") {
    from("../PRIVACY.md")
    from("../CHANGES.md")
    from("../NOTICE")
    from("../LICENSE.md")
    from("../HELP.md")
    into("src/main/assets")
}

tasks.getByName("preBuild") {
    dependsOn("copyToAssets")
}

tasks.getByName("copyToAssets") {
    dependsOn("cleanAssets")
}

android.applicationVariants.configureEach {
    val variantName = this.name

    val generatePackageLicenses =
        tasks.register<Exec>("generatePackageLicenses${variantName.capitalized()}") {
            commandLine(
                "python",
                "../licenses/packageLicenseParser.py",
                variantName
            )
    }.get()

    tasks.matching { it.name == "${name}OssLicensesTask" }
        .configureEach { generatePackageLicenses.dependsOn(this) }

    tasks.matching { it.name == "generate${name.capitalized()}Assets" }
        .configureEach { this.dependsOn(generatePackageLicenses) }

    generatePackageLicenses.dependsOn("cleanAssets")
}
