# Keep Tink classes intact
-keep class com.google.crypto.tink.** { *; }

# Keep Hilt / Dagger generated classes
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep domain models
-keepclassmembers class com.towerchat.app.data.model.** { *; }

# Remove all logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Obfuscate everything except Android framework calls
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
