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
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# native-lib.cpp / native-lib-aoba.cpp call back into these methods via
# JNI GetMethodID("onOutput", ...), so R8 must not rename or strip them.
-keepclassmembers class com.tksoft.shogigui.UsiEngine {
    void onOutput(java.lang.String);
}
-keepclassmembers class com.tksoft.shogigui.AobaEngine {
    void onOutput(java.lang.String);
}