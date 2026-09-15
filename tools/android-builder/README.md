# Android builder

Reproducible local builder for the Hypershell Portal downstream. It is pinned to an immutable Cirrus Android SDK linux-amd64 image digest and adds the Android 34 platform/build tools required by upstream Portal v0.7.25.

This exists so verification does not require Java or Android SDK packages on the Hypershell Docker host.
