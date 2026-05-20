plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}

// AGP 8.5.2 ships R8 8.5.35, which throws ConcurrentModificationException
// during parallel tree-shaking. Force the newer R8 from the R8 release channel.
buildscript {
    repositories {
        maven { url = uri("https://storage.googleapis.com/r8-releases/raw") }
    }
    dependencies {
        classpath("com.android.tools:r8:8.7.18")
    }
}
