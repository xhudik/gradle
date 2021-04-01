pluginManagement {
    includeBuild("../build-logic-base")
}

plugins {
    id("gradlebuild.settings-plugins")
}

include("code-quality")
include("code-quality-rules")
include("gradle-plugin")
