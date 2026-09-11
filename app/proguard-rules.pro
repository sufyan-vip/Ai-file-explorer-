# NEXARQ — R8/ProGuard rules

# Commons Compress (archive engine)
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.io.**
-keep class org.apache.commons.compress.** { *; }
-keep class org.tukaani.xz.** { *; }
-dontwarn org.tukaani.xz.**

# zip4j
-keep class net.lingala.zip4j.** { *; }
-dontwarn net.lingala.zip4j.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.nexarq.app.**$$serializer { *; }
-keepclassmembers class com.nexarq.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.nexarq.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep line numbers for readable crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
