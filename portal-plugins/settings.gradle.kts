pluginManagement {
    includeBuild("../build-logic/build-logic-base")
}

plugins {
    id("gradlebuild.settings-plugins")
}

includeBuild("../distribution-plugins/basics")
