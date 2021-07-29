plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.5.0"
    id("org.jetbrains.intellij") version "1.1.4"
    id("org.jetbrains.changelog") version "1.1.2"
}

group = "com.beaverkilla"
version = "1.8"

repositories {
    mavenCentral()
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    plugins.add("java")
    version.set("2021.2")
}

tasks {
    compileJava {
        options.release.set(11)
    }

    patchPluginXml {
        version.set("1.8")
        changeNotes.set("""
      Initial Version.<br>
      New token cause I uploaded the last to github ... durr.<br>
      Fix some dependency problems with the plugin.<br>
      Update the dependencies for intellij 2020.1.<br>
      Update for 2020.2.<br>
      Added a super constructor with no args, all args.<br>
      Moved all args above super+all args.<br>
      Update for 2020.3<br>
      Update for 2021.1<br>
      Update for 2021.2, Gradle upgraded to 7.1.1, removed the unneeded test dep, converted intellij plugin to 1.1.4 (upgrade build.gradle format).<br>
      """)
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
