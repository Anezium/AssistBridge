pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri("https://maven.rokid.com/repository/maven-public/") }
        mavenCentral()
    }
}

rootProject.name = "AssistBridge"
include(":protocol")
include(":phone-app")
include(":glasses-app")

val cxrGlobalBuild = when {
    file("CxrGlobal").isDirectory -> file("CxrGlobal")
    file("../CxrGlobal").isDirectory -> file("../CxrGlobal")
    else -> throw GradleException(
        "CxrGlobal is required. Run `git submodule update --init --recursive` " +
                "or clone https://github.com/Anezium/CxrGlobal next to AssistBridge."
    )
}

include(":cxrglobal-lib")
project(":cxrglobal-lib").projectDir = file("${cxrGlobalBuild.path}/lib")
