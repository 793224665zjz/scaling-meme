# ProGuard rules for Android apps

# Keep the application class
-keep public class * extends android.app.Application

# Keep all activity classes
-keep public class * extends android.app.Activity

# Keep all fragments
-keep public class * extends android.app.Fragment

# Keep all ViewModel classes
-keep public class * extends androidx.lifecycle.ViewModel

# Keep all annotations
-keepattributes *Annotation*

# Keep public methods and fields
-keep public class * {
    public *;
}

# Prevent obfuscation of specific classes
-keep class com.yourpackage.** { *; }