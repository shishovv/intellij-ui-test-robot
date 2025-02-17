plugins {
    id("org.jetbrains.intellij")
}
val robotServerVersion = if (System.getenv("SNAPSHOT") == null) {
    rootProject.ext["publish_version"] as String
} else {
    "0.0.1-SNAPSHOT"
}
version = robotServerVersion

repositories {
    maven("https://repo.labs.intellij.net/intellij")
}

dependencies {
    val ktor_version: String by project

    implementation(project(":remote-robot"))

    implementation("io.ktor:ktor-server-core:$ktor_version") {
        exclude("org.slf4j", "slf4j-api")
    }
    implementation("io.ktor:ktor-server-netty:$ktor_version") {
        exclude("org.slf4j", "slf4j-api")
    }
    implementation("io.ktor:ktor-gson:$ktor_version") {
        exclude("org.slf4j", "slf4j-api")
    }
    implementation("ch.qos.logback:logback-classic:1.2.10")
    implementation("org.assertj:assertj-swing-junit:3.17.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.8.2")

    implementation("org.mozilla:rhino:1.7.13")
}

// Create sources Jar from main kotlin sources
val sourcesJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles sources JAR"
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

intellij {
    updateSinceUntilBuild.set(false)
    version.set("LATEST-EAP-SNAPSHOT")
}

tasks {
    runIde {
        jbrVersion.set("11_0_2b159")
        systemProperty("robot-server.port", 8080)
        systemProperty("robot.encryption.enabled", "true")
        systemProperty("robot.encryption.password", "my super secret")
    }

    patchPluginXml {
        changeNotes.set("""
            api for robot inside Idea<br>
            <em>idea testing</em>
        """.trimIndent())
    }
}

publishing {
    publications {
        register("robotServerPlugin", MavenPublication::class) {
            artifact("build/distributions/robot-server-plugin-$robotServerVersion.zip")
            groupId = project.group as String
            artifactId = project.name
            version = robotServerVersion
        }
        register("robotServerJar", MavenPublication::class) {
            from(components["java"])
            groupId = project.group as String
            artifactId = "robot-server"
            version = rootProject.ext["publish_version"] as String
            val sourcesJar by tasks.getting(Jar::class)
            artifact(sourcesJar)
        }
    }
}
