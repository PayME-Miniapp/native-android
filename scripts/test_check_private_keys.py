import sys
sys.dont_write_bytecode = True
import base64
import importlib.util
import io
import os
from pathlib import Path
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('scanner', Path(__file__).with_name('check-private-keys.py'))
scanner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scanner)

DASHES = b'-' * 5


def der(tag, content):
    length = len(content)
    if length < 0x80:
        return bytes([tag, length]) + content
    size = (length.bit_length() + 7) // 8
    return bytes([tag, 0x80 | size]) + length.to_bytes(size, 'big') + content


def integer(value_bytes):
    return der(0x02, b'\x00' + value_bytes)


# Synthetic structures with random bytes: shaped like keys, but not keys.
def pkcs1_private():
    return der(0x30, der(0x02, b'\x00') + b''.join(integer(os.urandom(n)) for n in (128, 3, 128, 64, 64, 64, 64, 64)))


def pkcs8_private():
    algorithm = der(0x30, der(0x06, bytes.fromhex('2a864886f70d010101')) + b'\x05\x00')
    return der(0x30, der(0x02, b'\x00') + algorithm + der(0x04, pkcs1_private()))


def spki_public():
    algorithm = der(0x30, der(0x06, bytes.fromhex('2a864886f70d010101')) + b'\x05\x00')
    return der(0x30, algorithm + der(0x03, b'\x00' + der(0x30, integer(os.urandom(128)) + integer(b'\x01\x00\x01'))))


def b64(data):
    return base64.b64encode(data)


class ScannerTest(unittest.TestCase):
    header = DASHES + b'BEGIN PRIVATE KEY' + DASHES
    rsa_header = DASHES + b'BEGIN RSA PRIVATE KEY' + DASHES

    def test_header_alone_or_as_parser_constant_is_not_flagged(self):
        self.assertFalse(scanner.contains_private_key(self.header))
        constants = b'.replace("' + self.header + b'", "")\n    .replace("' + self.rsa_header + b'", "")\n'
        self.assertFalse(scanner.contains_private_key(constants))
        self.assertFalse(scanner.contains_private_key(b'public configuration only'))

    def test_header_followed_by_body_is_flagged(self):
        body = b64(os.urandom(48))
        self.assertTrue(scanner.contains_private_key(self.header + b'\n' + body))
        self.assertTrue(scanner.contains_private_key(b'"' + self.rsa_header + b'\\n" +\n  "' + body + b'"'))

    def test_bare_der_private_keys_are_flagged(self):
        self.assertTrue(scanner.contains_private_key(b'key = "' + b64(pkcs1_private()) + b'"'))
        self.assertTrue(scanner.contains_private_key(b'key = "' + b64(pkcs8_private()) + b'"'))

    def test_public_keys_and_short_runs_are_not_flagged(self):
        self.assertFalse(scanner.contains_private_key(b'key = "' + b64(spki_public()) + b'"'))
        self.assertFalse(scanner.contains_private_key(b'MIIC' + b'A' * 40))

    def test_zip_entries_are_scanned(self):
        archive = io.BytesIO()
        with zipfile.ZipFile(archive, 'w') as output:
            output.writestr('bundle.js', b'var k="' + b64(pkcs8_private()) + b'";')
        self.assertTrue(scanner.contains_private_key(archive.getvalue()))


if __name__ == '__main__':
    unittest.main()
