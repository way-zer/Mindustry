plugins {
    java
}

//dependencies {
//    implementation(project(":core"))
//    implementation(arcModule("extensions:discord"))
//    implementation(arcModule("natives:natives-desktop"))
//    implementation(arcModule("natives:natives-freetype-desktop"))
//}

val mainClassName = "mindustry.desktop.DesktopLauncher"

sourceSets.main {
    java.srcDirs("src/")
}

tasks {
    val run by registering(JavaExec::class) {
        mainClass.set(mainClassName)
        classpath(sourceSets.main.map { it.runtimeClasspath })
        standardInput = System.`in`
        workingDir = file("../core/assets")
        isIgnoreExitValue = true

        jvmArgs = buildList {
            if (System.getProperty("os.name").lowercase().contains("mac")) {
                add("-XstartOnFirstThread")
            }
            add("-XX:+ShowCodeDetailsInExceptionMessages")
            if (hasProperty("jvmArgs")) {
                addAll(property("jvmArgs").toString().split(" "))
            }
        }

        if (hasProperty("dataDir")) {
            environment("MINDUSTRY_DATA_DIR", property("dataDir").toString())
        }

        args = buildList {
            if (hasProperty("args")) {
                addAll(property("args").toString().split(" "))
            }
            if (contains("debug")) {
                mainClass = "mindustry.debug.DebugLauncher"
            }
        }
    }
    val dist by registering(Jar::class) {
        archiveFileName.set("${appName}.jar")
        manifest {
            attributes("Main-Class" to mainClassName)
        }

        from(sourceSets.main.map { it.output.classesDirs })
        from(sourceSets.main.map { it.output.resourcesDir })
        from(configurations.runtimeClasspath.map { c -> c.files.map { if (it.isDirectory) it else zipTree(it) } })
        from(getByPath(":core:allAssets"))
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        if (!versionModifier.contains("steam")) {
            exclude("**steam**.so", "**steam**.dll", "**steam**.dylib")
        }
    }

    val steamTest by registering(Copy::class) {
        from("build/libs/Mindustry.jar")
        if (hasProperty("destination")) {
            into(property("destination"))
        } else if (System.getProperty("os.name").contains("Mac")) {
            into("/Users/anuke/Library/Application Support/Steam/steamapps/common/Mindustry/Mindustry.app/Contents/Resources")
        } else {
            into("/home/anuke/.steam/steam/steamapps/common/Mindustry/jre")
        }
        rename("Mindustry.jar", "desktop.jar")
    }

    //required templates:
    //- Windows32: Not provided by Packr! This uses Java 8
    //required JDKs:
    //- Windows64
    //- Linux64
    //- Mac
    for (platform in listOf("Linux64", "Windows64", "Windows32", "MacOS")) {
        val jdkDir = System.getenv("JDK_DIR")
        val iconDir = "$rootDir/core/assets/icons/icon.icns"
        val packr = register("packr$platform") {
            dependsOn(dist)
            val workDir = temporaryDir
            val outDir = temporaryDir.resolve("output")
            outputs.dir(outDir)
            doLast {
                delete(outDir)
                copy {
                    into(workDir)
                    rename("${appName}.jar", "desktop.jar")
                    from("build/libs/${appName}.jar")
                }
                if (platform == "Windows32") {
                    copy {
                        into(workDir)
                        from("$jdkDir/templates/${platform.lowercase()}")
                    }

                    copy {
                        from("$workDir/desktop.jar")
                        into(workDir.resolve("jre"))
                    }
                } else {
                    val args = buildList {
                        add("java");add("-jar");add("$jdkDir/packr.jar")
                        add("--platform");add(if (platform == "MacOS") "Mac" else platform)
                        add("--jdk");add(jdkDir + "jre-${platform.lowercase()}")
                        add("--executable");add(appName)
                        add("--classpath");add("$workDir/desktop.jar")
                        add("--mainclass");add(mainClassName)
                        add("--verbose")
                        add("--bundle");add("mindustry.mac")
                        add("--icon");add(iconDir)
                        add("--output");add(outDir)
                        add("--removelibs");add("$workDir/desktop.jar")

                        add("--vmargs")
                        if (platform == "MacOS")
                            add("XstartOnFirstThread")
                        add("Dhttps.protocols=TLSv1.2,TLSv1.1,TLSv1")
                        add("XX:+ShowCodeDetailsInExceptionMessages")
                    }
                    exec {
                        commandLine(args)
                        standardOutput = System.out
                    }

                    if (platform != "MacOS") {
                        copy {
                            into("$outDir/jre/")
                            from("$outDir/desktop.jar")
                        }

                        delete {
                            delete("$outDir/desktop.jar")
                        }

                        file("$outDir/Mindustry.json").apply {
                            writeText(readText().replace("desktop.jar", "jre/desktop.jar"))
                        }
                    } else {
                        copy {
                            into("$outDir/${appName}.app/Contents/")
                            from("$outDir/Contents/")
                        }

                        delete {
                            delete("$outDir/Contents/")
                        }
                    }
                }
            }
        }
        val zip = register("zip$platform", Zip::class) {
            from(packr)
            destinationDirectory.set(file("../deploy"))
            archiveFileName.set("${generateDeployName(platform)}.zip")
        }
        packr { finalizedBy(zip) }
    }
}