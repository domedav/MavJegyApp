# kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.domedav.mavjegy.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.domedav.mavjegy.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# zxing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
