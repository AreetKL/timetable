# ML Kit 自带 consumer rules；Compose 亦然。仅做保守补充。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# protobuf-lite（ML Kit 内部使用）在部分版本缺规则
-dontwarn com.google.protobuf.**
