# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class io.github.snomu.termwidget.**$$serializer { *; }
-keepclassmembers class io.github.snomu.termwidget.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.snomu.termwidget.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# WorkManager creates its Room database by reflection. Without this, R8 removes
# the generated class's constructor and the app crashes on launch.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
