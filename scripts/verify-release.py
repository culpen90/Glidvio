#!/usr/bin/env python3
"""Verify and stage an already built signed APK; never accesses private signing credentials."""
from pathlib import Path
import hashlib
import os
import re
import shutil
import ssl
import struct
import subprocess
import zipfile

root = Path(__file__).resolve().parent.parent
sdk_candidates = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
local_properties = root / "local.properties"
if local_properties.exists():
    sdk_candidates += re.findall(r"^sdk\.dir=(.+)$", local_properties.read_text(), re.MULTILINE)
sdk_candidates += [str(Path.home() / "Library/Android/sdk"), str(Path.home() / "Android/Sdk")]
sdk = next((Path(p) for p in sdk_candidates if p and (Path(p) / "build-tools/36.0.0").is_dir()), None)
if sdk is None:
    raise SystemExit("Android build-tools 36.0.0 are required for release verification.")
tools = sdk / "build-tools/36.0.0"
apk = root / "app/build/outputs/apk/release/app-release.apk"
if not apk.is_file():
    raise SystemExit("A signed app-release.apk is required. Unsigned APKs are never staged for publication.")

def run(*args):
    result = subprocess.run([str(a) for a in args], text=True, capture_output=True)
    if result.returncode:
        raise SystemExit(f"Release verification failed: {Path(args[0]).name}\n{result.stdout}{result.stderr}")
    return result.stdout

certificates = run(tools / "apksigner", "verify", "--verbose", "--print-certs", "--print-certs-pem", apk)
if "Android Debug" in certificates:
    raise SystemExit("Refusing to publish an APK signed with a development certificate.")
if "Verified using v2 scheme (APK Signature Scheme v2): true" not in certificates:
    raise SystemExit("APK Signature Scheme v2 verification is required.")
run(tools / "zipalign", "-c", "-P", "16", "4", apk)
badging = run(tools / "aapt2", "dump", "badging", apk)
package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.MULTILINE)
if not package or package[1] != "com.glidvio.app" or "-dev" in package[3]:
    raise SystemExit("The public artifact must use the production package and release version.")
build_config = (root / "app/build.gradle.kts").read_text()
expected_code = re.search(r"\bversionCode\s*=\s*(\d+)", build_config)
expected_name = re.search(r'\bversionName\s*=\s*"([^"]+)"', build_config)
if not expected_code or not expected_name or (package[2], package[3]) != (expected_code[1], expected_name[1]):
    raise SystemExit("The APK version does not match the source release configuration.")
if "application-debuggable" in badging or "application-testOnly" in badging:
    raise SystemExit("Debuggable/test-only APKs must not be published.")
manifest = run(tools / "aapt2", "dump", "xmltree", apk, "--file", "AndroidManifest.xml")
if re.search(r"android:(?:debuggable|testOnly)\([^\n]*\)=\(type 0x12\)0xffffffff", manifest):
    raise SystemExit("The manifest enables a debug/test-only flag.")
if "sdkVersion:'26'" not in badging:
    raise SystemExit("Unexpected minimum SDK; update and review release requirements.")
pem = re.search(r"-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", certificates, re.DOTALL)
fingerprint = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)", certificates)
if not pem or not fingerprint:
    raise SystemExit("Could not extract the APK's public signing certificate.")
trusted_certificate = root / "signing/release-certificate.pem"
if not trusted_certificate.is_file():
    raise SystemExit("The established public release certificate is required in signing/release-certificate.pem.")
trusted_digest = hashlib.sha256(ssl.PEM_cert_to_DER_cert(trusted_certificate.read_text())).hexdigest()
if fingerprint[1].lower() != trusted_digest:
    raise SystemExit("The APK signer does not match the established public release certificate.")

with zipfile.ZipFile(apk) as archive:
    for asset, source in (("LICENSE.txt", "LICENSE"), ("THIRD_PARTY_NOTICES.txt", "THIRD_PARTY_NOTICES.md")):
        if archive.read(f"assets/{asset}") != (root / source).read_bytes():
            raise SystemExit(f"Packaged license does not match source: {asset}")
    # Verify actual native ELF load segments, in addition to ZIP entry alignment.
    # Android devices using 16 KiB pages need both forms of alignment.
    for entry in archive.namelist():
        if not entry.startswith("lib/") or not entry.endswith(".so"):
            continue
        data = archive.read(entry)
        if data[:4] != b"\x7fELF":
            raise SystemExit(f"Invalid native library: {entry}")
        endian = "<" if data[5] == 1 else ">"
        is64 = data[4] == 2
        phoff = struct.unpack_from(endian + ("Q" if is64 else "I"), data, 32 if is64 else 28)[0]
        phentsize, phnum = struct.unpack_from(endian + "HH", data, 54 if is64 else 42)
        for index in range(phnum):
            offset = phoff + index * phentsize
            if struct.unpack_from(endian + "I", data, offset)[0] != 1:
                continue
            alignment = struct.unpack_from(endian + ("Q" if is64 else "I"), data, offset + (48 if is64 else 28))[0]
            if alignment < (16384 if is64 else 4096):
                raise SystemExit(f"Native library lacks the required load alignment: {entry}")

output = Path(os.environ.get("GLIDVIO_RELEASE_OUTPUT", root / "artifacts/release"))
output.mkdir(parents=True, exist_ok=True)
staged = output / "Glidvio.apk"
shutil.copy2(apk, staged)
(output / "Glidvio-signing-certificate.pem").write_text(pem[0] + "\n")
digest = hashlib.sha256(staged.read_bytes()).hexdigest()
(output / "SHA256SUMS").write_text(f"{digest}  Glidvio.apk\n")
print(f"Verified Glidvio {package[3]} (code {package[2]}): signed, non-debuggable, aligned for 16 KiB pages.")
print(f"APK: {staged.stat().st_size:,} bytes; SHA-256: {digest}")
print(f"Signing certificate SHA-256: {fingerprint[1]}")
print(f"Release artifacts: {output}")
