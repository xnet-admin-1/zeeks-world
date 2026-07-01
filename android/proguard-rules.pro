-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep game classes for debugging
-keep class ngo.xnet.zeeksworld.** { *; }

# Keep kool engine
-keep class de.fabmax.kool.** { *; }
-dontwarn de.fabmax.kool.**

# Keep kotlin metadata
-keepattributes *Annotation*
-keep class kotlin.Metadata { *; }
