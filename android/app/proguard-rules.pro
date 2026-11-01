# ProGuard/R8 rules for release builds.
# Rules for specific libraries (Retrofit, kotlinx.serialization, Room) will be
# added here as the corresponding logic is implemented.

# Google ML Kit GenAI Prompt API / AICore
# ML Kit uses reflection to discover ComponentRegistrar classes and internal model loaders.
-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep component registrars loaded via reflection / manifest metadata
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
}
