"""ZIP/ELF fixtures prove report arithmetic and alignment checks without loading native code."""
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

from scripts.apk_size_report import analyze, compare, elf_info, markdown


def elf64(alignment=16384, offset=0, virtual=0):
    ident = b'\x7fELF\x02\x01\x01' + b'\0' * 9
    header = struct.pack('<HHIQQQIHHHHHH', 3, 183, 1, 0, 64, 0, 0, 64, 56, 1, 64, 0, 0)
    segment = struct.pack('<IIQQQQQQ', 1, 5, offset, virtual, virtual, 256, 256, alignment)
    return (ident + header + segment).ljust(256, b'\0')


class ApkSizeReportTest(unittest.TestCase):
    def test_elf_alignment_requires_both_page_alignment_and_congruent_addresses(self):
        self.assertTrue(elf_info(elf64())['compatible_16k'])
        self.assertFalse(elf_info(elf64(alignment=4096))['compatible_16k'])
        self.assertFalse(elf_info(elf64(offset=4096, virtual=0))['compatible_16k'])
        self.assertTrue(elf_info(elf64(offset=4096, virtual=4096))['compatible_16k'])

    def test_elf32_and_malformed_headers_are_explicitly_reported(self):
        ident = b'\x7fELF\x01\x01\x01' + b'\0' * 9
        header = struct.pack('<HHIIIIIHHHHHH', 3, 40, 1, 0, 52, 0, 0, 52, 32, 1, 40, 0, 0)
        segment = struct.pack('<IIIIIIII', 1, 0, 0, 0, 128, 128, 5, 16384)
        result = elf_info((ident + header + segment).ljust(128, b'\0'))
        self.assertEqual(32, result['bits']); self.assertTrue(result['compatible_16k'])
        for broken in (b'', b'not ELF', elf64()[:90]):
            result = elf_info(broken)
            self.assertFalse(result['valid']); self.assertIsNone(result['compatible_16k'])

    def test_zip_groups_exact_bytes_and_distinguishes_elf_from_zip_alignment(self):
        with tempfile.TemporaryDirectory() as temporary:
            apk = Path(temporary) / 'fixture.apk'
            library = 'lib/arm64-v8a/libfixture.so'
            info = zipfile.ZipInfo(library)
            padding = 16384 - 30 - len(library.encode())
            info.extra = struct.pack('<HH', 0xCAFE, padding - 4) + b'\0' * (padding - 4)
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr(info, elf64())
                archive.writestr('classes.dex', b'dex\nfixture')
                archive.writestr('assets/a.bin', b'a' * 4096, compress_type=zipfile.ZIP_DEFLATED)
                archive.writestr('assets/b.bin', b'a' * 4096, compress_type=zipfile.ZIP_DEFLATED)
                archive.writestr('lib/x86/libcompressed.so', b'not ELF', compress_type=zipfile.ZIP_DEFLATED)
                archive.writestr('assets/audiofx/fallback.so', elf64(alignment=4096))
                archive.writestr('res/raw/demo.txt', b'hello')
            result = analyze(apk, extract_native_libs='true')
            self.assertEqual(apk.stat().st_size, result['artifact']['apk_bytes'])
            self.assertEqual(result['artifact']['apk_bytes'], result['zip_payload_bytes'] + result['zip_container_and_signature_bytes'])
            self.assertEqual(8192 + 256, result['categories']['assets']['raw_bytes'])
            self.assertLess(result['categories']['assets']['zip_bytes'], 8192)
            native = result['entries'][0]
            self.assertTrue(native['zip_alignment_16k']); self.assertTrue(native['elf']['compatible_16k'])
            self.assertIn('lib/x86/libcompressed.so', result['alignment']['elf_unverified'])
            self.assertIn('assets/audiofx/fallback.so', result['alignment']['elf_16k_failures'])
            self.assertEqual(1, len(result['identical_content_groups']))
            self.assertIsNone(result['extraction']['additional_extracted_native_bytes_estimate'])
            selected = analyze(apk, extract_native_libs='true', device_abi='arm64-v8a')
            self.assertEqual(256, selected['extraction']['additional_extracted_native_bytes_estimate'])
            with self.assertRaises(ValueError):
                analyze(apk, extract_native_libs='true', device_abi='missing-abi')
            self.assertIn('Not measured installed size', markdown(result))

    def test_comparison_is_an_artifact_delta_and_does_not_claim_a_performance_gain(self):
        with tempfile.TemporaryDirectory() as temporary:
            before, after = Path(temporary) / 'before.apk', Path(temporary) / 'after.apk'
            for path, data in ((before, b'x' * 100), (after, b'x' * 20)):
                with zipfile.ZipFile(path, 'w') as archive:
                    archive.writestr('classes.dex', data)
            a, b = analyze(before, build_variant='debug'), analyze(after, build_variant='release')
            delta = compare(a, b)
            self.assertEqual(-80, delta['apk_bytes_delta'])
            self.assertEqual(-80, delta['changed_entries'][0]['zip_bytes_delta'])
            self.assertIn('debug-vs-release', delta['interpretation'])
            self.assertIsNone(a['extraction']['additional_extracted_native_bytes_estimate'])


if __name__ == '__main__':
    unittest.main()
