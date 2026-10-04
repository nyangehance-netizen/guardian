# Guardian — keep rules. The app uses no reflection-heavy libraries, so defaults suffice.
# Keep the admin receiver and services referenced from the manifest.
-keep class com.guardian.child.admin.** { *; }
-keep class com.guardian.child.enforce.** { *; }
