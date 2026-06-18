# Add project specific ProGuard rules here.
# Hilt requires keeping the generated component classes.
-keep class dagger.hilt.android.internal.managers.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
