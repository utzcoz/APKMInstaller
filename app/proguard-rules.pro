# Add project specific ProGuard rules here.
# Keep Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ActivityComponentManager { *; }

# Keep Kotlin data classes used as domain models
-keep class com.apkm.installer.domain.model.** { *; }
