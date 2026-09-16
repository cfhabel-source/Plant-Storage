plugins {
    java
    application
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Supported Jetty 12, using Jakarta Servlet 6 (EE10).
    implementation("org.eclipse.jetty:jetty-server:12.0.39")
    implementation("org.eclipse.jetty.ee10:jetty-ee10-servlet:12.0.39")

    // SQLite + Gson
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Google Drive API (core)
    implementation("com.google.api-client:google-api-client:2.2.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20230815-2.0.0")

    // REQUIRED for Java 17+ (including Java 21)
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")

    implementation("com.google.http-client:google-http-client-jackson2:1.43.3")

    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

application {
    mainClass.set("com.plantstorage.Main")
}

tasks.named<JavaExec>("run") {
    workingDir = projectDir
}

tasks.register<JavaExec>("setupDrive") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.plantstorage.DriveSetup")
}

// Standalone decision-tree checks; no OAuth credentials or Google Drive needed.
tasks.register<JavaExec>("testIdentification") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.plantstorage.decisiontree.PlantDecisionTreeTest")
}

tasks.named("check") {
    dependsOn("testIdentification")
}

// Bundle the browser assets in the installable distribution / Docker image.
distributions {
    main {
        contents { from("src/main/webapp") { into("webapp") } }
    }
}

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

tasks.register<JavaExec>("testSecurity") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.plantstorage.security.SecurityTest")
}
tasks.named("check") { dependsOn("testSecurity") }
