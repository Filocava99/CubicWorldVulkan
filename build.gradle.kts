plugins {
    id("java") // Keep this for now
    kotlin("jvm") version "1.9.0"
    application
}

group = "it.filippocavallari"
version = "1.0-SNAPSHOT"

// LWJGL Configuration
val lwjglVersion = "3.4.0-SNAPSHOT"
val jomlVersion = "1.10.7"
val jomlPrimitivesVersion = "1.10.0"

val lwjglNatives = when (org.gradle.internal.os.OperatingSystem.current()) {
    org.gradle.internal.os.OperatingSystem.LINUX -> "natives-linux"
    org.gradle.internal.os.OperatingSystem.MAC_OS -> if(System.getProperty("os.arch").startsWith("aarch64")) "natives-macos-arm64" else "natives-macos"
    org.gradle.internal.os.OperatingSystem.WINDOWS -> "natives-windows"
    else -> throw Error("Unrecognized or unsupported operating system. Please set lwjglNatives manually")
}

repositories {
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
}

dependencies {
    // Kotlin dependencies
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    
    // JUnit dependencies
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    
    // LWJGL dependencies
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-assimp")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-stb")
    implementation("org.lwjgl:lwjgl-vulkan")
    implementation("org.lwjgl:lwjgl-shaderc")
    implementation("org.lwjgl:lwjgl-openal")
    implementation("org.lwjgl:lwjgl-vma")
    
    // LWJGL native libraries
    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-assimp::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-stb::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-shaderc::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-openal::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-vma::$lwjglNatives")
    if (lwjglNatives == "natives-macos" || lwjglNatives == "natives-macos-arm64") {
        runtimeOnly("org.lwjgl:lwjgl-vulkan::$lwjglNatives")
    }
    
    // JOML (Java OpenGL Math Library)
    implementation("org.joml:joml:$jomlVersion")
    implementation("org.joml:joml-primitives:$jomlPrimitivesVersion")
    
    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("io.github.spair:imgui-java-natives-windows:1.89.0")
    implementation("io.github.spair:imgui-java-binding:1.89.0")
    implementation("io.github.spair:imgui-java-lwjgl3:1.89.0")
    implementation("org.tinylog:tinylog-api:2.8.0-M1")
    implementation("org.tinylog:tinylog-impl:2.8.0-M1")
}

tasks.test {
    useJUnitPlatform()
}

// Source sets configuration for Kotlin
sourceSets {
    main {
        java.srcDirs("src/main/java")  // Empty the Java source directories
        kotlin.srcDirs("src/main/kotlin")
        resources.srcDirs("src/main/resources")
    }
    test {
        java.srcDirs()  // Empty the Java source directories
        kotlin.srcDirs("src/test/kotlin")
        resources.srcDirs("src/test/resources")
    }
}

// Create necessary kotlin directories
tasks.register("createKotlinDirs") {
    doLast {
        mkdir("src/main/kotlin")
        mkdir("src/test/kotlin")
        mkdir("src/main/resources")
        mkdir("src/main/resources/shaders")
        mkdir("src/main/resources/textures")
    }
}

// Configure Java compilation
tasks.named<JavaCompile>("compileJava") {
    enabled = true
    targetCompatibility = "17"
    sourceCompatibility = "17"
}

tasks.named<JavaCompile>("compileTestJava") {
    enabled = false
}

// Make sure Kotlin directories exist before compilation
tasks.compileKotlin {
    dependsOn("createKotlinDirs")
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.compileTestKotlin {
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Configure the application plugin
application {
    mainClass.set("it.filippocavallari.cubicworld.MainKt")
}