#!/usr/bin/env python3
"""Fail when a tracked or untracked-not-ignored file (or a ZIP entry) holds a private key.

Flags a PEM private-key header only when a base64 body follows it, and bare base64 DER
RSA/PKCS#8 private keys. Prints file paths only, never key material.
"""
import base64
import io
import re
import subprocess
import sys
import zipfile
from pathlib import Path

# Header, then separators seen in source literals (whitespace, escaped newlines, quotes, +), then a body.
PEM_WITH_BODY = re.compile(
    rb'-{5}BEGIN (?:RSA |EC |ENCRYPTED |DSA |OPENSSH )?PRIVATE KEY-{5}'
    rb'(?:\s|\\[rnt]|["\'`+,])*[A-Za-z0-9+/]{64,}'
)
BARE_DER = re.compile(rb'MI[IG][A-Za-z0-9+/]{100,}')


def _tlv(data, offset):
    """Return (tag, content_start, content_end) of the DER element at offset, or None."""
    if offset + 2 > len(data):
        return None
    tag, length = data[offset], data[offset + 1]
    offset += 2
    if length & 0x80:
        count = length & 0x7F
        if count == 0 or count > 4 or offset + count > len(data):
            return None
        length = int.from_bytes(data[offset:offset + count], 'big')
        offset += count
    return tag, offset, offset + length


def is_private_key_der(der):
    """PKCS#1 SEQUENCE{INTEGER 0, INTEGER...} or PKCS#8 SEQUENCE{INTEGER 0, SEQUENCE, OCTET STRING}.

    Only the prefix is inspected, so a truncated decode of a long run is enough.
    """
    outer = _tlv(der, 0)
    if not outer or outer[0] != 0x30:
        return False
    version = _tlv(der, outer[1])
    if not version or version[0] != 0x02 or der[version[1]:version[2]] != b'\x00':
        return False
    second = _tlv(der, version[2])
    if not second:
        return False
    if second[0] == 0x02:
        return True
    if second[0] == 0x30:
        third = _tlv(der, second[2])
        return bool(third) and third[0] == 0x04
    return False


def _has_bare_der_key(data):
    for match in BARE_DER.finditer(data):
        run = match.group(0)
        run = run[:len(run) - len(run) % 4]
        try:
            der = base64.b64decode(run, validate=True)
        except ValueError:
            continue
        if is_private_key_der(der):
            return True
    return False


def _has_key_text(data):
    return bool(PEM_WITH_BODY.search(data)) or _has_bare_der_key(data)


def contains_private_key(data):
    if _has_key_text(data):
        return True
    if zipfile.is_zipfile(io.BytesIO(data)):
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            return any(_has_key_text(archive.read(item)) for item in archive.infolist() if not item.is_dir())
    return False


def main():
    root = Path(subprocess.check_output(['git', 'rev-parse', '--show-toplevel']).decode().strip())
    names = subprocess.check_output(
        ['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=root
    ).split(b'\0')
    failures = []
    for name in sorted(set(names)):
        if not name:
            continue
        path = root / name.decode()
        if path.is_file() and contains_private_key(path.read_bytes()):
            failures.append(name.decode())
    for name in failures:
        print(name)
    return int(bool(failures))


if __name__ == '__main__':
    sys.exit(main())
