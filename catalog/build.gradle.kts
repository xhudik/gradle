plugins {
    id("version-catalog")
}

group = "org.gradle"

configurations.versionCatalogElements {
    outgoing.artifacts.clear()
    outgoing.artifact(layout.projectDirectory.file("libs.versions.toml"))
}

