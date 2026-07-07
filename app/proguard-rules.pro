# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.navidabbasian.kibord.**$$serializer { *; }
-keepclassmembers class com.navidabbasian.kibord.** {
    *** Companion;
}
-keepclasseswithmembers class com.navidabbasian.kibord.** {
    kotlinx.serialization.KSerializer serializer(...);
}
