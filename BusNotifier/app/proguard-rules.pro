# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.navalrishi.busnotifier.**$$serializer { *; }
-keepclassmembers class com.navalrishi.busnotifier.** {
    *** Companion;
}
-keepclasseswithmembers class com.navalrishi.busnotifier.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Custom serializers referenced via @Serializable(with = ...)
-keep class com.navalrishi.busnotifier.network.StuListSerializer { *; }
-keep class com.navalrishi.busnotifier.network.StuListSerializer$* { *; }

# Coroutines internal classes (defensive)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}

# Tink (pulled in by androidx.security.crypto) references compile-time-only
# annotation classes that aren't in the runtime classpath.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.jspecify.annotations.**
-dontwarn org.checkerframework.**

# Lifecycle 2.8 moved LocalLifecycleOwner to lifecycle-runtime-compose; R8 was
# stripping it and crashing Compose with "CompositionLocal LocalLifecycleOwner
# not present". Keep the activity/lifecycle/navigation bridge classes.
-keep class androidx.activity.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }
-keep class androidx.savedstate.** { *; }
