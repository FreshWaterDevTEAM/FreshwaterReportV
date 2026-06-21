plugins {
    id("com.gradleup.shadow") version "8.3.5"
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    implementation(project(":common"))
}

tasks.shadowJar {
    archiveBaseName.set("FreshwaterReportV-VelocityCompanion")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
