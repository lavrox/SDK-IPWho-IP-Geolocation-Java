plugins {
    `java-library`
    signing
    id("com.vanniktech.maven.publish") version "0.34.0"
}

group = "com.lavrox"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
}

repositories {
    mavenCentral()
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

signing {
    useGpgCmd()
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("com.lavrox", "ipwho", "1.0.0")
    pom {
        name.set("IPWho Java SDK")
        description.set("Official Java client for the IPWho IP geolocation API.")
        inceptionYear.set("2026")
        url.set("https://github.com/lavrox/SDK-IPWho-IP-Geolocation-Java")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("lavrox")
                name.set("Lavrox")
                url.set("https://lavrox.com")
            }
        }
        scm {
            url.set("https://github.com/lavrox/SDK-IPWho-IP-Geolocation-Java")
            connection.set("scm:git:git://github.com/lavrox/SDK-IPWho-IP-Geolocation-Java.git")
            developerConnection.set("scm:git:ssh://git@github.com/lavrox/SDK-IPWho-IP-Geolocation-Java.git")
        }
    }
}
