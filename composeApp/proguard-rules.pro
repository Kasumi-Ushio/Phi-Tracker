# R8: com.google.errorprone.annotations.Immutable is compile-time annotation metadata
# referenced by com.google.crypto.tink.KeyTemplate (via AndroidX Security Crypto).
# Not reachable at runtime from app code.
-dontwarn com.google.errorprone.annotations.Immutable
