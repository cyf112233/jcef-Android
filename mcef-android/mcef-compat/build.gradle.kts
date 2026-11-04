plugins { id("java-library") }

repositories { mavenCentral() }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// MCEFBrowser / MCEFRenderer 只用到 LWJGL 的 GL11 和 GLFW 常量，
// 不依赖任何 net.minecraft.* 类，所以这里不需要 MC 的 build 环境。
dependencies {
    // GL11 在 lwjgl-opengl 里，不在 core 里（core 只有 org.lwjgl.system 那些）
    compileOnly("org.lwjgl:lwjgl:3.3.3")
    compileOnly("org.lwjgl:lwjgl-opengl:3.3.3")
}

// 不要用 --release 17：MC 侧 class 可能是更高版本
tasks.withType<JavaCompile>().configureEach {
    options.release.set(null)
    options.encoding = "UTF-8"
}

tasks.jar {
    archiveBaseName.set("mcef-android")
    archiveVersion.set("1.0.0")
}
