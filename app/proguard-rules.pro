# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Wear OS Watch Face - 保持所有表盘相关类
-keep class androidx.wear.watchface.** { *; }
-keep class * extends androidx.wear.watchface.WatchFaceService { *; }
-keepclassmembers class * extends androidx.wear.watchface.WatchFaceService {
    public <init>(...);
}
-keep class * extends androidx.wear.watchface.Renderer$* { *; }

# Wear OS Tiles - 保持 Tile 服务
-keep class androidx.wear.tiles.** { *; }
-keep class * extends androidx.wear.tiles.TileService { *; }
-keepclassmembers class * extends androidx.wear.tiles.TileService {
    public <init>(...);
}

# Compose
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# Keep data models
-keep class massey.hamhuo.timetagger.data.model.** { *; }

# Keep Parcelables
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Kotlin
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}