# kotlinx.serialization: @Serializable-Klassen behalten (Backup-JSON, OFF-DTOs)
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class de.frelina.vorrat.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class de.frelina.vorrat.**$$serializer { *; }
