-keep class com.imad.planner.** { *; }

# Google sign-in through Credential Manager (from its documentation).
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
