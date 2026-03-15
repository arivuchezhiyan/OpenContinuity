# Add project specific ProGuard rules here.
-keep class com.opencontinuity.core.protocol.** { *; }
-keepclassmembers class com.opencontinuity.core.protocol.** { *; }

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.opencontinuity.**$$serializer { *; }
-keepclassmembers class com.opencontinuity.** { *** Companion; }
-keepclasseswithmembers class com.opencontinuity.** { kotlinx.serialization.KSerializer serializer(...); }
