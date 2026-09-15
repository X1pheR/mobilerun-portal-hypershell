FROM ghcr.io/cirruslabs/android-sdk@sha256:54e4ef10f19c211052851736dfb783ee26935a089b06bb1f9019a311cdc38af0

USER root
RUN yes | sdkmanager --licenses >/dev/null \
    && sdkmanager "platforms;android-34" "build-tools;34.0.0"

# Builder execution is intentionally non-root; the verifier also maps the
# invoking host UID/GID explicitly when mounting maintained source.
USER 1000:1000
