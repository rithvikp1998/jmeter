/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.github.vlsi.gradle.crlf.CrLfSpec
import com.github.vlsi.gradle.crlf.LineEndings
import com.github.vlsi.gradle.crlf.filter
import com.github.vlsi.gradle.dsl.configureEach

plugins {
    id("java")
    id("eclipse")
    id("idea")
    id("com.github.vlsi.crlf")
    id("com.github.vlsi.gradle-extensions")
    id("build-logic.test-base")
    id("build-logic.build-params")
    id("build-logic.style")
    id("build-logic.test-junit5")
}

java {
    toolchain {
        configureToolchain(buildParameters.buildJdk)
    }
    consistentResolution {
        useRuntimeClasspathVersions()
    }
}

tasks.configureEach<JavaCompile> {
    // Use --release=<targetJavaVersion> for javac so the generated bytecode does not include methods introduced in
    // next Java releases
    options.release.set(buildParameters.targetJavaVersion)
}

tasks.configureEach<JavaExec> {
    buildParameters.testJdk?.let {
        javaLauncher.convention(javaToolchains.launcherFor(it))
    }
}

tasks.configureEach<Checkstyle> {
    buildParameters.buildJdk?.let {
        javaLauncher.convention(javaToolchains.launcherFor(it))
    }
}

dependencies {
    findProject(":src:bom")?.let {
        api(platform(it))
    }
    findProject(":src:bom-testing")?.let{
        testImplementation(platform(it))
        plugins.withId("java-test-fixtures") {
            "testFixturesImplementation"(platform(it))
        }
    }
    findProject(":src:bom-thirdparty")?.let{
        api(platform(it))
        annotationProcessor(platform(it))
        compileOnlyApi(platform(it))
    }
}

tasks.register<DependencyInsightReportTask>("allDependencyInsight") {
    group = HelpTasksPlugin.HELP_GROUP
    description =
        "Shows insights where the dependency is used. For instance: allDependencyInsight --configuration compile --dependency org.jsoup:jsoup"
}

tasks.configureEach<JavaCompile> {
    inputs.property("java.version", System.getProperty("java.version"))
    inputs.property("java.vm.version", System.getProperty("java.vm.version"))
    options.apply {
        encoding = "UTF-8"
        compilerArgs.add("-Xlint:deprecation")
//        if (buildParameters.werror) {
//            compilerArgs.add("-Werror")
//        }
    }
}

tasks.configureEach<ProcessResources> {
    filteringCharset = "UTF-8"
    eachFile {
        if (name.endsWith(".properties")) {
            filteringCharset = "UTF-8"
            // apply native2ascii conversion since Java 8 expects properties to have ascii symbols only
            filter(org.apache.tools.ant.filters.EscapeUnicode::class)
            filter(LineEndings.LF)
        } else if (name.endsWith(".dtd") || name.endsWith(".svg") ||
            name.endsWith(".txt")
        ) {
            filter(LineEndings.LF)
        }
    }
}

tasks.configureEach<Javadoc> {
    (options as StandardJavadocDocletOptions).apply {
        noTimestamp.value = true
        showFromProtected()
        locale = "en"
        docEncoding = "UTF-8"
        charSet = "UTF-8"
        encoding = "UTF-8"
        docTitle = "Apache JMeter ${project.name} API"
        windowTitle = "Apache JMeter ${project.name} API"
        header = "<b>Apache JMeter</b>"
        addStringOption("source", "8")
        addStringOption("Xmaxwarns", "10")
        addBooleanOption("Xdoclint:all,-missing", true)
        val lastEditYear: String by rootProject.extra
        bottom =
            "Copyright &copy; 1998-$lastEditYear Apache Software Foundation. All Rights Reserved."
        if (buildParameters.buildJdkVersion > 8) {
            addBooleanOption("html5", true)
            links("https://docs.oracle.com/en/java/javase/11/docs/api/")
        } else {
            links("https://docs.oracle.com/javase/8/docs/api/")
        }
    }
}

// Add default license/notice when missing (e.g. see :src:config that overrides LICENSE)
afterEvaluate {
    tasks.configureEach<Jar> {
        CrLfSpec(LineEndings.LF).run {
            into("META-INF") {
                filteringCharset = "UTF-8"
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                // Note: we need "generic Apache-2.0" text without third-party items
                // So we use the text from $rootDir/config/ since source distribution
                // contains altered text at $rootDir/LICENSE
                textFrom("$rootDir/config/LICENSE")
                textFrom("$rootDir/NOTICE")
            }
        }
    }
}

tasks.configureEach<Jar> {
    manifest {
        attributes["Bundle-License"] = "Apache-2.0"
        attributes["Specification-Title"] = "Apache JMeter"
        attributes["Specification-Vendor"] = "Apache Software Foundation"
        attributes["Implementation-Vendor"] = "Apache Software Foundation"
        attributes["Implementation-Vendor-Id"] = "org.apache"
        attributes["Implementation-Version"] = rootProject.version
    }
}
