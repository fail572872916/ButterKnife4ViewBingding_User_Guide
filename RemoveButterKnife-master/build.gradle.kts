plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.imiyar"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

intellij {
    // 本地 IDEA 路径
    localPath.set("C:/Users/fail5/AppData/Local/Programs/IntelliJ IDEA Community Edition")
    plugins.set(listOf("java"))
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs += "-Xskip-metadata-version-check"
        }
    }

    patchPluginXml {
        sinceBuild.set("252")
        untilBuild.set("252.*")
    }
}
