plugins { id("java-library") }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// 不要用 --release 17：MC 侧 class 可能是更高版本，--release 会把 API 视图收窄
tasks.withType<JavaCompile>().configureEach {
    options.release.set(null)
    options.encoding = "UTF-8"
}

tasks.jar { archiveBaseName.set("mcef-android"); archiveVersion.set("1.0.0") }
