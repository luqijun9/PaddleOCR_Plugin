# Proguard rules for PaddleOCR Plugin

# Keep PaddleOCR and JNI native libraries
-keep class com.paddle.ocr.** { *; }
-keepclassmembers class com.paddle.ocr.** { *; }

# Keep ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }

# Keep OpenCV
-keep class org.opencv.** { *; }
-keepclassmembers class org.opencv.** { *; }

# Keep Tasker Plugin standard classes and serialized bundles
-keep class com.twofortyfouram.** { *; }
-keep class net.dinglisch.** { *; }

# Keep Compose runtime & animations
-keep class androidx.compose.** { *; }
