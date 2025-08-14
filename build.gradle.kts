plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.gradle.signing)
//    id("com.vanniktech.maven.publish") version "0.34.0"
}

android {
    namespace = "com.example.deviceinfolib"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
            resources.srcDirs("src/main/resources")
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
//            withJavadocJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.biometric)
    implementation(libs.security.crypto)
    testImplementation(libs.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

publishing {
    publications {
        create<MavenPublication>("release") {
            // The groupId, artifactId, and version will be taken from
            // the project properties or explicitly set here.
            // AGP might also provide default values for these based on your variant.
            // It's good practice to set them explicitly for clarity.

            groupId = "com.example.deviceinfolib" // Or project.group.toString()
            artifactId = "deviceinfolib"       // Or project.name
            version = "1.0.0"                // Or project.version.toString()

            // If you need to ensure this configuration happens after AGP has
            // set up its components, you can use afterEvaluate, though it's
            // often not necessary with `singleVariant`.
            // Defer accessing components until after project evaluation
            project.afterEvaluate {
                from(components.findByName("release"))
                // Or, if you know the component is definitely 'java' or 'web' (less likely for Android release)
                // from(components.getByName("java"))
            }

        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Aram-dev/deviceinfotestlibrary")
            credentials {
                username =
                    project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
                password =
                    project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

//mavenPublishing {
////    publishToMavenCentral(automaticRelease = true)
//    coordinates(
//        groupId = "com.example.deviceinfotestlibrary",
//        artifactId = "deviceinfotestlibrary",
//        version = "1.0.0-SNAPSHOT"
//    )
//
//    pom {
//        name.set("Deviceinfo test library")
//        description.set("Collects some device-specific data.")
//        inceptionYear.set("2025")
//        url.set("https://github.com/Aram-dev/deviceinfotestlibrary/")
//        licenses {
//            license {
//                name.set("The Apache License, Version 2.0")
//                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
//                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
//            }
//        }
//        developers {
//            developer {
//                id.set("Aram-dev")
//                name.set("Aram Babujyan")
//                url.set("https://github.com/Aram-dev/")
//            }
//        }
//        scm {
//            url.set("https://github.com/Aram-dev/deviceinfotestlibrary/")
//            connection.set("scm:git:git://github.com/Aram-dev/deviceinfotestlibrary.git")
//            developerConnection.set("scm:git:ssh://git@github.com/Aram-dev/deviceinfotestlibrary.git")
//        }
//    }
//}

//signing {
//    useGpgCmd()
//    // Only sign if the 'release' property is set (e.g., -Prelease from command line)
//    // AND all necessary signing properties are present.
//    val isReleaseVersion = project.hasProperty("release")
//    val allSigningPropertiesPresent = project.hasProperty("signing.keyId") &&
//            project.hasProperty("signing.password") &&
//            project.hasProperty("signing.secretKeyRingFile")
//
//    // Use a more robust check for whether signing should proceed
//    isRequired = isReleaseVersion && allSigningPropertiesPresent
//
//    // If still using sign(publication) make sure it's within a conditional block
//    // or the isRequired above handles it.
//    if (isRequired) { // .get() if isRequired is a Provider
//        sign(publishing.publications)
//    }
//}

tasks.register("listComponents") {
    doLast {
        println("Available components:")
        components.forEach { println(it.name) }
    }
}

//tasks.withType<Sign>().configureEach {
//    onlyIf {
//        // This might be too simplistic if properties aren't always set
//        gradle.taskGraph.hasTask(":publishMavenPublicationToMavenRepository")
//    }
//    // If signing.keyId is not set here, the internal spec will fail
//}

