plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.gradle.signing)
    id("com.vanniktech.maven.publish") version "0.34.0"
}

android {
    namespace = "com.example.deviceinfotest"
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
//    publishing {
//        singleVariant("release") {
//            // If you want to include sources/javadoc jars, you can add:
//            // withSourcesJar()
//            // withJavadocJar()
//        }
//    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

//publishing {
//    publications {
////        create<MavenPublication>("release") {
////            // Configure publication after the project has been evaluated
////            afterEvaluate {
////                from(components.findByName("release"))
////            }
////            groupId = "com.example.deviceinfotestlibrary"
////            artifactId = "deviceinfotestlibrary"
////            version = "1.0.0"
////        }
//
//        create<MavenPublication>("debug") {
//            // Configure publication after the project has been evaluated
//            afterEvaluate {
//                from(components.findByName("debug"))
//            }
//            groupId = "com.example.deviceinfotestlibrary"
//            artifactId = "deviceinfotestlibrary"
//            version = "1.0.1"
//        }
//    }
//    repositories {
//        maven {
//            name = "GitHubPackages"
//            url = uri("https://maven.pkg.github.com/Aram-dev/deviceinfotestlibrary")
//            credentials {
//                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
//                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
//            }
//        }
//    }
//}

mavenPublishing {
    coordinates(
        groupId = "com.example.deviceinfotestlibrary",
        artifactId = "deviceinfotestlibrary",
        version = "1.0.0-SNAPSHOT"
    )

    pom {
        name.set("Deviceinfo test library")
        description.set("Collects some device-specific data.")
        inceptionYear.set("2020")
        url.set("https://github.com/Aram-dev/deviceinfotestlibrary/")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("Aram-dev")
                name.set("Aram Babujyan")
                url.set("https://github.com/Aram-dev/")
            }
        }
        scm {
            url.set("https://github.com/Aram-dev/deviceinfotestlibrary/")
            connection.set("scm:git:git://github.com/Aram-dev/deviceinfotestlibrary.git")
            developerConnection.set("scm:git:ssh://git@github.com/Aram-dev/deviceinfotestlibrary.git")
        }
    }
}

tasks.register("listComponents") {
    doLast {
        println("Available components:")
        components.forEach { println(it.name) }
    }
}

