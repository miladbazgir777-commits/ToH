# Milestone 10 keeps shrinking disabled for the first release candidate.
# These rules make later R8 enablement safe for serialized model names and Android callbacks.
-keepattributes *Annotation*
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class com.tomeofhealing.app.model.** { *; }
-keep,includedescriptorclasses class com.tomeofhealing.app.backup.** { *; }
