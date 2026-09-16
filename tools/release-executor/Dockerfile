FROM ghcr.io/cirruslabs/android-sdk@sha256:54e4ef10f19c211052851736dfb783ee26935a089b06bb1f9019a311cdc38af0

USER root

RUN yes | sdkmanager --licenses >/dev/null \
    && sdkmanager "platforms;android-34" "build-tools;34.0.0"

# Install only the locked runtime needed by config.py/provider.py from the
# accepted Bitwarden Secrets Manager MCP v0.2.0 release. Wheels are fetched by
# immutable URL and verified before extraction; no package resolver is involved.
RUN python3 - <<'PY'
from hashlib import sha256
from pathlib import Path
from urllib.request import urlopen
from zipfile import ZipFile

artifacts = [
    (
        "https://github.com/X1pheR/bitwarden-secrets-manager-mcp/releases/download/v0.2.0/bitwarden_secrets_manager_mcp-0.2.0-py3-none-any.whl",
        "683f44eba56b43d9533b5127d3cbb6adf8cea894cf091502cf8afc48cbd6cb00",
    ),
    (
        "https://files.pythonhosted.org/packages/7e/f4/ccdcadee82f88ec3c52a3b6e0db11df4c5587db171a2c979e4e9eb76b1d4/bitwarden_sdk-2.1.0-cp39-abi3-manylinux_2_17_x86_64.manylinux2014_x86_64.whl",
        "b6f6b2624e340307891edc20a2860ec0b7c2140683eec2ce2e20d1076ffe9268",
    ),
    (
        "https://files.pythonhosted.org/packages/ec/57/56b9bcc3c9c6a792fcbaf139543cee77261f3651ca9da0c93f5c1221264b/python_dateutil-2.9.0.post0-py2.py3-none-any.whl",
        "a8b2bc7bffae282281c8140a97d3aa9c14da0b136dfe83f850eea9a5f7470427",
    ),
    (
        "https://files.pythonhosted.org/packages/b7/ce/149a00dd41f10bc29e5921b496af8b574d8413afcd5e30dfa0ed46c2cc5e/six-1.17.0-py2.py3-none-any.whl",
        "4721f391ed90541fddacab5acf947aa0d3dc7d27b2e1e8eda2be8970586c3274",
    ),
]
root = Path("/opt/portal-release/site-packages")
root.mkdir(parents=True, exist_ok=True)
for index, (url, expected) in enumerate(artifacts):
    data = urlopen(url, timeout=60).read()
    observed = sha256(data).hexdigest()
    if observed != expected:
        raise SystemExit(f"wheel hash mismatch at index {index}")
    wheel = Path(f"/tmp/release-dependency-{index}.whl")
    wheel.write_bytes(data)
    with ZipFile(wheel) as archive:
        archive.extractall(root)
    wheel.unlink()
PY

ENV PYTHONPATH=/opt/portal-release/site-packages

# The runtime launcher maps the host operator UID/GID explicitly; this image
# contains no credentials and no persistent signing material.
USER 1000:1000
