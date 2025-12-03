import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val sharedVersion = "1.0.0"

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.1.10"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
    id("com.gradleup.shadow") version "8.3.6"
}

subprojects {
    group = "no.nav.emottak"
    version = sharedVersion
}

tasks {
    ktlintFormat {
        enabled = true
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        freeCompilerArgs = listOf(
            "-opt-in=kotlin.uuid.ExperimentalUuidApi,arrow.fx.coroutines.await.ExperimentalAwaitAllApi"
        )
    }
}
