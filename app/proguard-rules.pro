# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep ONNX Runtime classes
-keep class ai.onnxruntime.** { *; }

# Keep Room classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Keep ML model related classes
-keep class com.mindforge.app.ml.** { *; }

# Keep data classes
-keepclassmembers class com.mindforge.app.data.** { *; }
