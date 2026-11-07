# kotlinx.serialization: @Serializable-Klassen behalten (Backup-JSON, OFF-DTOs)
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class de.example.vorrat.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class de.example.vorrat.**$$serializer { *; }
