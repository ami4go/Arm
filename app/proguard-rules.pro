# ProGuard rules for Arm Translator
# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep all classes in our package
-keep class com.arm.translator.** { *; }

# Keep sherpa-onnx classes
-keep class com.k2fsa.sherpa.onnx.** { *; }
