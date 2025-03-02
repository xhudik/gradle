/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.performance.regression.android

import org.gradle.integtests.fixtures.versions.AndroidGradlePluginVersions
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.AndroidTestProject
import org.gradle.profiler.mutations.AbstractCleanupMutator
import org.gradle.profiler.mutations.ClearArtifactTransformCacheMutator
import spock.lang.Unroll

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.fixture.AndroidTestProject.LARGE_ANDROID_BUILD
import static org.gradle.performance.results.OperatingSystem.LINUX

class RealLifeAndroidBuildPerformanceTest extends AbstractCrossVersionPerformanceTest implements AndroidPerformanceTestFixture {

    def setup() {
        runner.args = [AndroidGradlePluginVersions.OVERRIDE_VERSION_CHECK]
        runner.targetVersions = ["7.1-20210412220040+0000"]
        AndroidTestProject.useStableAgpVersion(runner)
        // AGP 4.1 requires 6.5+
        // forUseAtConfigurationTime API used in this scenario
        runner.minimumBaseVersion = "6.5"
    }

    @Unroll
    @RunFor([
        @Scenario(type = PER_COMMIT, operatingSystems = LINUX, testProjects = "largeAndroidBuild", iterationMatcher = "run help"),
        @Scenario(type = PER_COMMIT, operatingSystems = LINUX, testProjects = ["largeAndroidBuild", "santaTrackerAndroidBuild"], iterationMatcher = "run assembleDebug"),
        @Scenario(type = PER_COMMIT, operatingSystems = LINUX, testProjects = "largeAndroidBuild", iterationMatcher = ".*phthalic.*")
    ])
    def "run #tasks"() {
        given:
        AndroidTestProject testProject = androidTestProject
        testProject.configure(runner)
        runner.tasksToRun = tasks.split(' ')
        runner.args.add('-Dorg.gradle.parallel=true')
        runner.warmUpRuns = warmUpRuns
        runner.runs = runs
        applyEnterprisePlugin()

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        tasks                          | warmUpRuns | runs
        'help'                         | null       | null
        'assembleDebug'                | null       | null
        'clean phthalic:assembleDebug' | 2          | 8
    }

    @RunFor([
        @Scenario(type = PER_DAY, operatingSystems = LINUX, testProjects = ["largeAndroidBuild", "santaTrackerAndroidBuild"], iterationMatcher = "clean assemble.*"),
        @Scenario(type = PER_DAY, operatingSystems = LINUX, testProjects = "largeAndroidBuild", iterationMatcher = "clean phthalic.*")
    ])
    @Unroll
    def "clean #tasks with clean transforms cache"() {
        given:
        def testProject = androidTestProject
        boolean isLargeProject = androidTestProject == LARGE_ANDROID_BUILD
        if (isLargeProject) {
            runner.warmUpRuns = 2
            runner.runs = 8
        }

        testProject.configure(runner)
        runner.tasksToRun = tasks.split(' ')
        runner.args.add('-Dorg.gradle.parallel=true')
        runner.cleanTasks = ["clean"]
        runner.useDaemon = false
        runner.addBuildMutator { invocationSettings ->
            new ClearArtifactTransformCacheMutator(invocationSettings.getGradleUserHome(), AbstractCleanupMutator.CleanupSchedule.BUILD)
        }
        applyEnterprisePlugin()

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        tasks << ['assembleDebug', 'phthalic:assembleDebug']
    }
}
