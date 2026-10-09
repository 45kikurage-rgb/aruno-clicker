#!/usr/bin/env python3
"""Require exactly the existing production signer, after apksigner verification."""
import os
from pathlib import Path
import re
import subprocess
import sys

EXPECTED_SHA256 = '60c71e1a71df53c94189835a798bec6a93cd1cc10be7c5c64609570554db1a04'


def verify(apk, tool):
    result = subprocess.run([tool, 'verify', '--print-certs', apk], capture_output=True, text=True)
    # Do not print subject DN, alias, or raw diagnostics.
    fingerprints = re.findall(r'^(?:Signer #\d+|V[1-4] Signer):? certificate SHA-256 digest: ([0-9a-fA-F]{64})$',
                              result.stdout, re.MULTILINE)
    if result.returncode or [v.lower() for v in fingerprints] != [EXPECTED_SHA256]:
        raise ValueError('APK signature verification failed or certificate does not match.')
    return fingerprints[0].lower()


def main():
    if len(sys.argv) != 3:
        raise ValueError('Usage: verify-apk-signature.py APK PROOF_FILE')
    sdk = Path(os.environ['ANDROID_HOME']) / 'build-tools'
    versions = [p for p in sdk.iterdir() if (p / 'apksigner').is_file()]
    tools = max(versions, key=lambda p: tuple(int(x) for x in re.findall(r'\d+', p.name)))
    digest = verify(sys.argv[1], str(tools / 'apksigner'))
    Path(sys.argv[2]).write_text('certificate_sha256=' + digest + '\n', encoding='utf-8')
    print('APK signature verified: ' + digest)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError):
        print('APK signature verification failed; distribution blocked.', file=sys.stderr)
        sys.exit(1)
