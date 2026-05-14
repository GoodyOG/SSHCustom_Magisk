# Add project-specific ProGuard rules here.
# AGP applies the SDK + library defaults already; only need to keep our
# kotlinx.serialization @Serializable models from being renamed.

# Keep generated kotlinx.serialization companions
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.sshcustom.app.**$$serializer { *; }
-keepclassmembers class com.sshcustom.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.sshcustom.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp pulls in optional Conscrypt/BouncyCastle hooks; we never use them
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# libsu reflective access
-keep class com.topjohnwu.superuser.** { *; }
