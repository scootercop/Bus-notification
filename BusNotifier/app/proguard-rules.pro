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
