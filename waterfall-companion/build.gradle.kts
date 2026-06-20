plugins {
    id("com.gradleup.shadow") version "8.3.5"
}

dependencies {
    compileOnly("io.github.waterfallmc:waterfall-api:1.20-R0.1-SNAPSHOT")
    implementation(project(":common"))
}

tasks.shadowJar {
    archiveBaseName.set("FreshwaterReportV-Companion")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("bungee.yml") {
        expand(props)
    }
}
