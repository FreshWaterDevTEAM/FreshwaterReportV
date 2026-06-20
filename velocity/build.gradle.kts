plugins {
    id("com.gradleup.shadow") version "8.3.5"
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    compileOnly("com.google.code.gson:gson:2.10.1")

    implementation(project(":common"))
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.mysql:mysql-connector-j:8.4.0")
    implementation("org.yaml:snakeyaml:2.2")
}

tasks.shadowJar {
    archiveBaseName.set("FreshwaterReportV-Velocity")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())

    // 重定位连接池/YAML，避免与其它插件冲突；MySQL 驱动不重定位以保证驱动类名可被 Class.forName 加载
    relocate("com.zaxxer.hikari", "com.freshwater.report.lib.hikari")
    relocate("org.yaml.snakeyaml", "com.freshwater.report.lib.snakeyaml")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
