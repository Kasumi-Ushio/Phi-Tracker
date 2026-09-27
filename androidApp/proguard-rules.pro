# R8: com.google.errorprone.annotations.* is compile-time annotation metadata
# referenced by com.google.crypto.tink (via AndroidX Security Crypto).
# Not reachable at runtime from app code.
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.RestrictedApi
