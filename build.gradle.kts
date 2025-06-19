plugins {
    id("java")
    id("maven-publish")
}

// Toolchains:
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

// Dependencies:
repositories {
    mavenCentral()
}

val annotationProc: Configuration by configurations.creating {
    configurations.compileOnly.get().extendsFrom(this)
    configurations.annotationProcessor.get().extendsFrom(this)
    configurations.testCompileOnly.get().extendsFrom(this)
    configurations.testAnnotationProcessor.get().extendsFrom(this)
}

dependencies {
    annotationProc("org.projectlombok:lombok:1.18.34")
}

// Tasks:
tasks.compileJava {
    options.encoding = "UTF-8"
}

tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks.javadoc)
}

// Publishing:
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "me.darragh"
            artifactId = "event-bus"
            version = project.version.toString()

            pom {
                name.set("Event Bus")
                description.set("A simple event bus for Java")
                url.set("https://github.com/darraghd493/event-bus")
                properties.set(mapOf(
                    "java.version" to "17",
                    "project.build.sourceEncoding" to "UTF-8",
                    "project.reporting.outputEncoding" to "UTF-8"
                ))
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://github.com/darraghd493/event-bus/blob/main/LICENSE")
                    }
                }
                developers {
                    developer {
                        id.set("darraghd493")
                        name.set("Darragh")
                    }
                }
                organization {
                    name.set("darragh.website")
                    url.set("https://darragh.website")
                }
                scm {
                    connection.set("scm:git:git://github.com/darraghd493/event-bus.git")
                    developerConnection.set("scm:git:ssh://github.com/darraghd493/event-bus.git")
                    url.set("https://github.com/darraghd493/event-bus")
                }
            }

            java {
                withSourcesJar()
                withJavadocJar()
            }
        }
    }

    repositories {
        mavenLocal()
        maven {
            name = "darraghsRepo"
            url = uri("https://repo.darragh.website/releases")
            credentials {
                username = System.getenv("REPO_TOKEN")
                password = System.getenv("REPO_SECRET")
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}
