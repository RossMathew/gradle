/*
 * Copyright 2018 the original author or authors.
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

package org.gradle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.featurelifecycle.DeprecationWarningProgressDetails

class DeprecationMessageBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emits deprecation warnings as build operation progress events with context"() {
        when:
        file('settings.gradle') << "rootProject.name = 'root'"

        file('init.gradle') << """
            org.gradle.util.DeprecationLogger.nagUserOfDeprecated('init deprecation');
        """

        file('script.gradle') << """
            org.gradle.util.DeprecationLogger.nagUserOfDeprecated('script deprecation');
        """

        buildScript """
            apply from: 'script.gradle'
            apply plugin: SomePlugin
            
            task t(type:SomeTask) {
                doLast {
                    org.gradle.util.DeprecationLogger.nagUserOfDeprecated('t task deprecation');
                }
            }
            
            task t2(type:SomeTask)
            
            class SomePlugin implements Plugin<Project> {
                void apply(Project p){
                    org.gradle.util.DeprecationLogger.nagUserOfDeprecated('plugin deprecation');
                }
            }
            
            class SomeTask extends DefaultTask {
                @TaskAction
                void someAction(){
                    org.gradle.util.DeprecationLogger.nagUserOfDeprecated('typed task deprecation');
                }
            }
        """
        and:
        executer.noDeprecationChecks()
        succeeds 't','t2', '-I', 'init.gradle'

        then:
        def initDeprecation = operations.only("Apply script init.gradle to build").progress.find {it.hasDetailsOfType(DeprecationWarningProgressDetails)}
        initDeprecation.details.message.contains('init deprecation')
        initDeprecation.details.stackTrace.size > 0
        initDeprecation.details.stackTrace[0].fileName.endsWith('init.gradle')
        initDeprecation.details.stackTrace[0].lineNumber == 2

        def pluginDeprecation = operations.only("Apply plugin SomePlugin to root project 'root'").progress.find {it.hasDetailsOfType(DeprecationWarningProgressDetails)}
        pluginDeprecation.details.message.contains('plugin deprecation')
        pluginDeprecation.details.stackTrace.size > 0
        pluginDeprecation.details.stackTrace[0].fileName.endsWith('build.gradle')
        pluginDeprecation.details.stackTrace[0].lineNumber == 15

        def scriptPluginDeprecation = operations.only("Apply script script.gradle to root project 'root'").progress.find {it.hasDetailsOfType(DeprecationWarningProgressDetails)}
        scriptPluginDeprecation.details.message.contains('script deprecation')
        scriptPluginDeprecation.details.stackTrace.size > 0
        scriptPluginDeprecation.details.stackTrace[0].fileName.endsWith('script.gradle')
        scriptPluginDeprecation.details.stackTrace[0].lineNumber == 2

        def taskDoLastDeprecation = operations.only("Execute doLast {} action for :t").progress.find {it.hasDetailsOfType(DeprecationWarningProgressDetails)}
        taskDoLastDeprecation.details.message.contains('t task deprecation')
        taskDoLastDeprecation.details.stackTrace.size > 0
        taskDoLastDeprecation.details.stackTrace[0].fileName.endsWith('build.gradle')
        taskDoLastDeprecation.details.stackTrace[0].lineNumber == 7

        def typedTaskDeprecation = operations.only("Execute someAction for :t").progress.find {it.hasDetailsOfType(DeprecationWarningProgressDetails)}
        typedTaskDeprecation.details.message.contains('typed task deprecation')
        typedTaskDeprecation.details.stackTrace.size > 0
        typedTaskDeprecation.details.stackTrace[0].fileName.endsWith('build.gradle')
        typedTaskDeprecation.details.stackTrace[0].lineNumber == 22
        typedTaskDeprecation.details.stackTrace[0].methodName == 'someAction'

        def typedTaskDeprecation2 = operations.only("Execute someAction for :t2").progress.find {it.hasDetailsOfType(DeprecationWarningProgressDetails)}
        typedTaskDeprecation2.details.message.contains('typed task deprecation')
        typedTaskDeprecation2.details.stackTrace.size > 0
        typedTaskDeprecation2.details.stackTrace[0].fileName.endsWith('build.gradle')
        typedTaskDeprecation2.details.stackTrace[0].lineNumber == 22
        typedTaskDeprecation2.details.stackTrace[0].methodName == 'someAction'
    }

    def "emits deprecation warnings as build operation progress events for buildSrc builds"() {
        when:
        file('buildSrc/build.gradle') << """
            org.gradle.util.DeprecationLogger.nagUserOfDeprecated('buildSrc script deprecation');
        """

        and:
        executer.noDeprecationChecks()
        succeeds 'help'

        then:
        def buildSrcDeprecations = operations.only("Apply script build.gradle to project ':buildSrc'").progress.find {it.hasDetailsOfType(DeprecationWarningProgressDetails)}
        buildSrcDeprecations.details.message.contains('buildSrc script deprecation')
        buildSrcDeprecations.details.stackTrace.size > 0
        buildSrcDeprecations.details.stackTrace[0].fileName.endsWith("buildSrc${File.separator}build.gradle")
        buildSrcDeprecations.details.stackTrace[0].lineNumber == 2
    }

    def "emits deprecation warnings as build operation progress events for composite builds"() {
        file('included/settings.gradle') << "rootProject.name = 'included'"
        file('included/build.gradle') << """
            org.gradle.util.DeprecationLogger.nagUserOfDeprecated('included build script deprecation');
            
            task t {
                doLast {
                    org.gradle.util.DeprecationLogger.nagUserOfDeprecated('included build task deprecation');
                }
            }
        """
        file('settings.gradle') << """
        rootProject.name = 'root'
        includeBuild('included')
        """

        when:
        buildFile << """
            task t { dependsOn gradle.includedBuilds*.task(':t') } 
        """

        and:
        executer.noDeprecationChecks()
        succeeds 't'

        then:
        def includedBuildScriptDeprecations = operations.only("Apply script build.gradle to project ':included'").progress.find {it.hasDetailsOfType(DeprecationWarningProgressDetails)}
        includedBuildScriptDeprecations.details.message.contains('included build script deprecation')
        includedBuildScriptDeprecations.details.stackTrace.size > 0
        includedBuildScriptDeprecations.details.stackTrace[0].fileName.endsWith("included${File.separator}build.gradle")
        includedBuildScriptDeprecations.details.stackTrace[0].lineNumber == 2

        def includedBuildTaskDeprecations = operations.only("Execute doLast {} action for :included:t").progress.find {it.hasDetailsOfType(DeprecationWarningProgressDetails)}
        includedBuildTaskDeprecations.details.message.contains('included build task deprecation')
        includedBuildTaskDeprecations.details.stackTrace.size > 0
        includedBuildTaskDeprecations.details.stackTrace[0].fileName.endsWith("included${File.separator}build.gradle")
        includedBuildTaskDeprecations.details.stackTrace[0].lineNumber == 6
    }
}
