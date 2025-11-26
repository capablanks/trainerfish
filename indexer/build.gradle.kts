plugins {
    kotlin("jvm") version "2.0.21"
}

kotlin { jvmToolchain(17) }

// Run the generator without the application plugin
tasks.register<JavaExec>("runIndexer") {
    group = "application"
    description = "Run the PGN index builder"
    mainClass.set("BuildPgnIndexKt")                 // top-level main() in BuildPgnIndex.kt
    classpath = sourceSets["main"].runtimeClasspath  // use module classpath
    standardInput = System.`in`
}
