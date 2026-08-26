-keep class com.nothing.ketchum.** { *; }
-dontwarn com.nothing.ketchum.**
-keep class com.nothing.thirdparty.** { *; }
-dontwarn com.nothing.thirdparty.**

# Keep models for Firebase Realtime Database
-keep class com.glyphix.app.model.** { *; }
-keepclassmembers class com.glyphix.app.model.** {
    <init>(...);
    private <fields>;
    public <fields>;
}
