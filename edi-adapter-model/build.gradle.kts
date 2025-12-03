import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.1.10"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
    id("maven-publish")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}

tasks {
    register<Wrapper>("wrapper") {
        gradleVersion = "8.1.1"
    }
    test {
        useJUnitPlatform()
    }
    ktlintFormat {
        this.enabled = true
    }
    ktlintCheck {
        dependsOn("ktlintFormat")
    }
    build {
        dependsOn("ktlintCheck")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        freeCompilerArgs = listOf(
            "-opt-in=kotlin.uuid.ExperimentalUuidApi,kotlin.time.ExperimentalTime"
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "edi-adapter-model"
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/navikt/${rootProject.name}")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
