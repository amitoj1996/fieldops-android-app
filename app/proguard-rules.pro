# Retrofit keeps interfaces and generic signatures.
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature, Exceptions, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations, RuntimeInvisibleParameterAnnotations
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Gson reflectively instantiates data classes and reads their fields. Keep
# our DTOs verbatim so release builds deserialize correctly.
-keep class com.fieldops.app.network.** { *; }
-keepclassmembers class com.fieldops.app.network.** { *; }
-keepattributes *Annotation*,EnclosingMethod,Signature,InnerClasses

# OkHttp has its own rules but add defensive keep for platform classes.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
