/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */
 
/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.opensearch.hadoop.gradle

import org.opensearch.gradle.info.BuildParams
import org.opensearch.gradle.info.GlobalBuildInfoPlugin
import org.opensearch.gradle.info.JavaHome
import org.opensearch.hadoop.gradle.util.Resources
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Configures global and shared configurations for all subprojects regardless of their role.
 */
class BaseBuildPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        greet(project)
        configureBuildInfo(project)
        configureVersions(project)
        configureRuntimeSettings(project)
        configureRepositories(project)
    }

    /**
     * Say hello!
     */
    private static void greet(Project project) {
        if (!project.rootProject.hasProperty('versionsConfigured') && !project.rootProject.hasProperty('shush')) {
            println '==========================================='
            println 'OpenSearch-Hadoop Build Hamster says Hello!'
            println '==========================================='
        }
    }

    private static void configureBuildInfo(Project project) {
        // Make sure the global build info plugin is applied to the root project first and foremost
        // Todo: Remove this once this is generated by the plugin
        project.rootProject.ext.testSeed = "DEADBEEF"
        project.rootProject.pluginManager.apply(GlobalBuildInfoPlugin.class)

        // Hack new defaults into Global build info
        if (!project.rootProject.ext.has('buildInfoConfigured')) {

            JavaVersion minimumRuntimeVersion = JavaVersion.toVersion(Resources.getResourceContents("/minimumRuntimeVersion"))
            println "Min runtime: ${minimumRuntimeVersion}"

            // We snap the runtime to java 8 since Hadoop needs to see some significant
            // upgrades to support any runtime higher than that
            JavaHome opensearchHadoopRuntimeJava = BuildParams.javaVersions.find { it.version == 8 }
            if (opensearchHadoopRuntimeJava == null) {
                throw new GradleException(
                        '$JAVA8_HOME must be set to build OpenSearch-Hadoop. ' +
                                "Note that if the variable was just set you might have to run `./gradlew --stop` for " +
                                "it to be picked up. See https://github.com/elastic/elasticsearch/issues/31399 details."
                )
            }

            // Set on global build info
            BuildParams.init { params ->
                params.setMinimumRuntimeVersion(minimumRuntimeVersion)
            }

            // Set on build settings
            project.rootProject.ext.runtimeJavaHome = opensearchHadoopRuntimeJava.javaHome.get()
            project.rootProject.ext.minimumRuntimeVersion = minimumRuntimeVersion

            project.rootProject.ext.buildInfoConfigured = true
        }
        // Propagate to current project
        project.ext.runtimeJavaHome = project.rootProject.ext.runtimeJavaHome
        project.ext.minimumRuntimeVersion = project.rootProject.ext.minimumRuntimeVersion
    }

    /**
     * Extract version information and load it into the build's extra settings
     * @param project to be configured
     */
    private static void configureVersions(Project project) {
        if (!project.rootProject.ext.has('versionsConfigured')) {
            project.rootProject.version = OshVersionProperties.OPENSEARCH_HADOOP_VERSION
            println "Building version [${project.rootProject.version}]"

            project.rootProject.ext.opensearchHadoopVersion = OshVersionProperties.OPENSEARCH_HADOOP_VERSION
            project.rootProject.ext.opensearchVersion = OshVersionProperties.OPENSEARCH_VERSION
            project.rootProject.ext.luceneVersion = OshVersionProperties.LUCENE_VERSION
            project.rootProject.ext.buildToolsVersion = OshVersionProperties.BUILD_TOOLS_VERSION
            project.rootProject.ext.versions = OshVersionProperties.VERSIONS
            project.rootProject.ext.versionsConfigured = true

            println "Testing against OpenSearch [${project.rootProject.ext.opensearchVersion}] with Lucene [${project.rootProject.ext.luceneVersion}]"

            println "Using Gradle [${project.gradle.gradleVersion}]"

            // Hadoop versions
            project.rootProject.ext.hadoopDistro = project.hasProperty("distro") ? project.getProperty("distro") : "hadoop3"
            switch (project.rootProject.ext.hadoopDistro) {
            // Hadoop YARN/2.0.x
                case "hadoop3":
                    project.rootProject.ext.hadoopVersion = project.hadoop3Version
                    println "Using Apache Hadoop on YARN [$project.hadoop3Version]"
                    break
                case "hadoopYarn":
                    project.rootProject.ext.hadoopVersion = project.hadoop2Version
                    println "Using Apache Hadoop on YARN [$project.hadoop2Version]"
                    break
                case "hadoopStable":
                    project.rootProject.ext.hadoopVersion = project.hadoop22Version
                    println "Using Apache Hadoop [$project.hadoop22Version]"
                    break
                default:
                    throw new GradleException("Invalid [hadoopDistro] setting: [$project.rootProject.ext.hadoopDistro]")
            }
            project.rootProject.ext.hadoopClient = ["org.apache.hadoop:hadoop-client:$project.rootProject.ext.hadoopVersion"]
        }
        project.ext.opensearchHadoopVersion = project.rootProject.ext.opensearchHadoopVersion
        project.ext.opensearchVersion = project.rootProject.ext.opensearchVersion
        project.ext.luceneVersion = project.rootProject.ext.luceneVersion
        project.ext.buildToolsVersion = project.rootProject.ext.buildToolsVersion
        project.ext.versions = project.rootProject.ext.versions
        project.ext.hadoopVersion = project.rootProject.ext.hadoopVersion
        project.ext.hadoopClient = project.rootProject.ext.hadoopClient
        project.version = project.rootProject.version
    }

    /**
     * Determine dynamic or runtime-based information and load it into the build's extra settings
     * @param project to be configured
     */
    private static void configureRuntimeSettings(Project project) {
        if (!project.rootProject.ext.has('settingsConfigured')) {
            // Force any Elasticsearch test clusters to use packaged java versions if they have them available
            project.rootProject.ext.isRuntimeJavaHomeSet = false
            project.rootProject.ext.settingsConfigured = true
        }
        project.ext.javaVersions = BuildParams.javaVersions
        project.ext.isRuntimeJavaHomeSet = project.rootProject.ext.isRuntimeJavaHomeSet
    }

    /**
     * Add all the repositories needed to pull dependencies for the build
     * @param project to be configured
     */
    private static void configureRepositories(Project project) {
        project.repositories.mavenCentral()
        project.repositories.maven { url = "https://clojars.org/repo" }
        project.repositories.maven { url = 'https://repo.spring.io/plugins-release-local' }

        // For OpenSearch snapshots.
        project.repositories.maven { url = "https://artifacts.opensearch.org/snapshots/" } // default
        project.repositories.maven { url = "https://central.sonatype.com/repository/maven-snapshots/" } // central portal snapshot
        project.repositories.maven { url = "https://aws.oss.sonatype.org/content/repositories/snapshots" } // oss-only

        // OpenSearch artifacts
//        project.repositories.maven { url "https://artifacts.opensearch.org/snapshots/" } // default
//        project.repositories.maven { url "https://aws.oss.sonatype.org/content/groups/public/" } // oss-only

        // Add Ivy repos in order to pull OpenSearch distributions that have bundled JDKs
        for (String repo : ['snapshots', 'artifacts']) {
            project.repositories.ivy {
                url = "https://${repo}.opensearch.org/releases"
                patternLayout {
                    artifact "/core/opensearch/[revision]/[module]-min-[revision](-[classifier]).[ext]"
                }
            }
        }

        // For Lucene Snapshots, Use the lucene version interpreted from opensearch-build-tools version file.
        if (project.ext.luceneVersion.contains('-snapshot')) {
            // Extract the revision number of the snapshot via regex:
            String revision = (project.ext.luceneVersion =~ /\w+-snapshot-([a-z0-9]+)/)[0][1]
            project.repositories.maven {
                name = 'lucene-snapshots'
                url = "https://artifacts.opensearch.org/snapshots/lucene/${revision}"
            }
        }
    }
}