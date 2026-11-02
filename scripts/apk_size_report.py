#!/usr/bin/env python3
"""Reproducible APK ZIP/ELF inventory; never rewrites the APK or infers runtime performance."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

PAGE_SIZE = 16384


def category(name: str) -> str:
    if name.endswith('.dex') and '/' not in name:
        return 'dex'
    if name.startswith('lib/') and name.endswith('.so'):
        return 'native'
    if name.startswith('assets/'):
        return 'assets'
    if name.startswith('res/'):
        return 'resources'
    if name == 'resources.arsc':
        return 'resource_table'
    if name == 'AndroidManifest.xml':
        return 'manifest'
    if name.startswith('META-INF/'):
        return 'metadata'
    return 'other'


def elf_info(data: bytes) -> dict:
    """Inspect PT_LOAD requirements without executing or loading native code."""
    try:
        if data[:4] != b'\x7fELF' or len(data) < 52 or data[5] not in (1, 2):
            raise ValueError('not a supported ELF header')
        endian = '<' if data[5] == 1 else '>'
        if data[4] == 2:
            if len(data) < 64:
                raise ValueError('truncated ELF64 header')
            phoff, shoff = struct.unpack_from(endian + 'QQ', data, 32)
            phentsize, phnum, shentsize, shnum, shstrndx = struct.unpack_from(endian + 'HHHHH', data, 54)
            phformat, shformat = 'IIQQQQQQ', 'IIQQQQIIQQ'
            bits = 64
        elif data[4] == 1:
            phoff, shoff = struct.unpack_from(endian + 'II', data, 28)
            phentsize, phnum, shentsize, shnum, shstrndx = struct.unpack_from(endian + 'HHHHH', data, 42)
            phformat, shformat = 'IIIIIIII', 'IIIIIIIIII'
            bits = 32
        else:
            raise ValueError('unsupported ELF class')
        loads = []
        if phnum and (phentsize < struct.calcsize(endian + phformat) or phoff + phentsize * phnum > len(data)):
            raise ValueError('truncated program header table')
        for i in range(phnum):
            ph = struct.unpack_from(endian + phformat, data, phoff + i * phentsize)
            if ph[0] != 1:
                continue
            offset, virtual, alignment = (ph[2], ph[3], ph[7]) if bits == 64 else (ph[1], ph[2], ph[7])
            compatible = alignment >= PAGE_SIZE and alignment & (alignment - 1) == 0 and offset % PAGE_SIZE == virtual % PAGE_SIZE
            loads.append({'offset': offset, 'virtual_address': virtual, 'alignment': alignment,
                          'compatible_16k': compatible})
        sections = []
        if shnum:
            if shentsize < struct.calcsize(endian + shformat) or shoff + shentsize * shnum > len(data):
                raise ValueError('truncated section header table')
            sections = [struct.unpack_from(endian + shformat, data, shoff + i * shentsize) for i in range(shnum)]
        names = b''
        if sections and shstrndx < len(sections):
            section = sections[shstrndx]
            names = data[section[4]:section[4] + section[5]]
        debug_bytes = 0
        for section in sections:
            start = section[0]
            end = names.find(b'\0', start)
            name = names[start:end] if end >= start else b''
            if name.startswith((b'.debug_', b'.zdebug_')):
                debug_bytes += section[5]
        return {'valid': True, 'bits': bits, 'machine': struct.unpack_from(endian + 'H', data, 18)[0],
                'load_segments': loads, 'compatible_16k': all(x['compatible_16k'] for x in loads) if loads else None,
                'has_static_symbol_table': any(x[1] == 2 for x in sections), 'debug_section_bytes': debug_bytes}
    except (ValueError, struct.error, IndexError) as error:
        return {'valid': False, 'compatible_16k': None, 'error': str(error)}


def data_offset(raw, info: zipfile.ZipInfo) -> int:
    raw.seek(info.header_offset)
    header = raw.read(30)
    if len(header) != 30 or header[:4] != b'PK\x03\x04':
        raise ValueError(f'invalid local ZIP header for {info.filename}')
    name_length, extra_length = struct.unpack_from('<HH', header, 26)
    return info.header_offset + 30 + name_length + extra_length


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def analyze(apk: Path, *, label: str = '', source_revision: str = '', build_variant: str = '',
            extract_native_libs: str = 'unknown', device_abi: str = '') -> dict:
    entries, categories, abis, duplicates = [], {}, {}, {}
    with zipfile.ZipFile(apk) as archive, apk.open('rb') as raw:
        for info in archive.infolist():
            if info.is_dir():
                continue
            group = category(info.filename)
            item = {'path': info.filename, 'category': group, 'raw_bytes': info.file_size,
                    'zip_bytes': info.compress_size, 'compression': 'STORED' if info.compress_type == 0 else 'DEFLATED' if info.compress_type == 8 else str(info.compress_type)}
            position = data_offset(raw, info)
            item['data_offset'] = position
            item['zip_alignment_4'] = position % 4 == 0 if info.compress_type == 0 else None
            digest = hashlib.sha256()
            with archive.open(info) as stream:
                for block in iter(lambda: stream.read(1024 * 1024), b''):
                    digest.update(block)
            item['sha256'] = digest.hexdigest()
            if info.file_size >= 4096:
                duplicates.setdefault((digest.hexdigest(), info.file_size), []).append(info.filename)
            # Imported-engine fallback adapters also ship ELF files under assets/.
            # Inspect those without counting them as package-manager extracted ABI libs.
            if info.filename.endswith('.so'):
                item['elf'] = elf_info(archive.read(info))
            if group == 'native':
                item['abi'] = info.filename.split('/')[1]
                item['zip_alignment_16k'] = position % PAGE_SIZE == 0 if info.compress_type == 0 else None
                bucket = abis.setdefault(item['abi'], {'entries': 0, 'raw_bytes': 0, 'zip_bytes': 0})
                bucket['entries'] += 1; bucket['raw_bytes'] += info.file_size; bucket['zip_bytes'] += info.compress_size
            bucket = categories.setdefault(group, {'entries': 0, 'raw_bytes': 0, 'zip_bytes': 0})
            bucket['entries'] += 1; bucket['raw_bytes'] += info.file_size; bucket['zip_bytes'] += info.compress_size
            entries.append(item)
    native = [entry for entry in entries if entry['category'] == 'native']
    elfs = [entry for entry in entries if 'elf' in entry]
    native_bytes = sum(entry['raw_bytes'] for entry in native)
    apk_bytes = apk.stat().st_size
    if device_abi and abis and device_abi not in abis:
        raise ValueError(f'APK has no native libraries for requested ABI: {device_abi}')
    extracted = (abis.get(device_abi, {}).get('raw_bytes', 0) if device_abi else None) if extract_native_libs == 'true' else 0 if extract_native_libs == 'false' else None
    return {'schema_version': 1, 'artifact': {'path': str(apk.resolve()), 'label': label, 'source_revision': source_revision,
            'build_variant': build_variant, 'sha256': sha256_file(apk), 'apk_bytes': apk_bytes},
            'categories': categories, 'abis': abis, 'entries': entries,
            'zip_payload_bytes': sum(item['zip_bytes'] for item in entries),
            'zip_container_and_signature_bytes': apk_bytes - sum(item['zip_bytes'] for item in entries),
            'native_library_raw_bytes': native_bytes,
            'extraction': {'extract_native_libs': extract_native_libs,
                'selected_device_abi': device_abi or None,
                'per_abi_native_raw_bytes': {abi: item['raw_bytes'] for abi, item in abis.items()},
                'additional_extracted_native_bytes_estimate': extracted,
                'note': 'Estimate only for the explicitly selected ABI; Android does not extract every ABI. Excludes ART compilation, filesystem allocation, user data and cache. Not measured installed size.'},
            'alignment': {'stored_entry_4byte_failures': [x['path'] for x in entries if x['zip_alignment_4'] is False],
                'stored_native_16k_failures': [x['path'] for x in native if x['zip_alignment_16k'] is False],
                'elf_16k_failures': [x['path'] for x in elfs if x['elf']['compatible_16k'] is False],
                'elf_unverified': [x['path'] for x in elfs if x['elf']['compatible_16k'] is None],
                'note': 'ELF PT_LOAD and ZIP offsets are static requirements, not proof of loading or playback on a 16 KiB device.'},
            'identical_content_groups': [{'sha256': key[0], 'raw_bytes_each': key[1], 'paths': paths}
                for key, paths in duplicates.items() if len(paths) > 1]}


def compare(before: dict, after: dict) -> dict:
    paths_before = {item['path']: item for item in before['entries']}
    paths_after = {item['path']: item for item in after['entries']}
    changed = []
    for path in sorted(paths_before.keys() | paths_after.keys()):
        old, new = paths_before.get(path), paths_after.get(path)
        delta = (new or {}).get('zip_bytes', 0) - (old or {}).get('zip_bytes', 0)
        if delta or (old or {}).get('sha256') != (new or {}).get('sha256'):
            changed.append({'path': path, 'zip_bytes_delta': delta,
                            'before_zip_bytes': (old or {}).get('zip_bytes', 0), 'after_zip_bytes': (new or {}).get('zip_bytes', 0)})
    return {'before_artifact': before['artifact'], 'apk_bytes_delta': after['artifact']['apk_bytes'] - before['artifact']['apk_bytes'],
            'native_raw_bytes_delta': after['native_library_raw_bytes'] - before['native_library_raw_bytes'],
            'changed_entries': sorted(changed, key=lambda item: abs(item['zip_bytes_delta']), reverse=True),
            'interpretation': 'Measured artifact delta only. Attribute improvements only when source, toolchain, ABI, signing and features were controlled; debug-vs-release is not such a comparison.'}


def markdown(report: dict) -> str:
    artifact = report['artifact']
    lines = ['# APK size and native alignment report', '', f"- Artifact: `{artifact['path']}`", f"- Label: {artifact['label']}",
             f"- Source: `{artifact['source_revision']}`; variant: `{artifact['build_variant']}`", f"- SHA-256: `{artifact['sha256']}`",
             f"- APK: {artifact['apk_bytes']:,} bytes ({artifact['apk_bytes'] / 1048576:.2f} MiB)",
             f"- ZIP container/signature overhead: {report['zip_container_and_signature_bytes']:,} bytes", '',
             '| Category | Files | Raw bytes | ZIP bytes | APK share |', '| --- | ---: | ---: | ---: | ---: |']
    for name, item in sorted(report['categories'].items(), key=lambda pair: pair[1]['zip_bytes'], reverse=True):
        lines.append(f"| {name} | {item['entries']} | {item['raw_bytes']:,} | {item['zip_bytes']:,} | {item['zip_bytes'] / artifact['apk_bytes']:.1%} |")
    lines += ['', '## Native libraries', '', '| ABI | Files | Raw bytes | ZIP bytes |', '| --- | ---: | ---: | ---: |']
    for name, item in sorted(report['abis'].items()):
        lines.append(f"| {name} | {item['entries']} | {item['raw_bytes']:,} | {item['zip_bytes']:,} |")
    lines += ['', '| Library | ELF 16 KiB | ZIP 16 KiB (STORED only) | Debug section bytes | Static symbols |', '| --- | --- | --- | ---: | --- |']
    for item in report['entries']:
        if 'elf' in item:
            elf = item['elf']
            lines.append(f"| `{item['path']}` | {elf['compatible_16k']} | {item.get('zip_alignment_16k')} | {elf.get('debug_section_bytes', 'unknown')} | {elf.get('has_static_symbol_table', 'unknown')} |")
    lines += ['', report['alignment']['note'], '', '## Largest ZIP entries', '', '| Path | Raw bytes | ZIP bytes | Method |', '| --- | ---: | ---: | --- |']
    for item in sorted(report['entries'], key=lambda entry: entry['zip_bytes'], reverse=True)[:25]:
        lines.append(f"| `{item['path']}` | {item['raw_bytes']:,} | {item['zip_bytes']:,} | {item['compression']} |")
    lines += ['', '## Installation estimate', '', f"- extractNativeLibs: `{report['extraction']['extract_native_libs']}`", f"- Selected device ABI: `{report['extraction']['selected_device_abi']}`", f"- Extra extracted native bytes estimate: {report['extraction']['additional_extracted_native_bytes_estimate']}", report['extraction']['note']]
    if 'comparison' in report:
        delta = report['comparison']
        lines += ['', '## Artifact comparison', '', f"- Baseline SHA-256: `{delta['before_artifact']['sha256']}`", f"- APK bytes delta (after - before): {delta['apk_bytes_delta']:+,}", f"- Native raw bytes delta: {delta['native_raw_bytes_delta']:+,}", delta['interpretation']]
    return '\n'.join(lines) + '\n'


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--json', dest='json_path', type=Path)
    parser.add_argument('--markdown', type=Path)
    parser.add_argument('--compare', type=Path, help='Prior JSON report; numeric deltas are not causal performance claims')
    parser.add_argument('--label', default='')
    parser.add_argument('--source-revision', default='')
    parser.add_argument('--build-variant', default='')
    parser.add_argument('--extract-native-libs', choices=('true', 'false', 'unknown'), default='unknown')
    parser.add_argument('--device-abi', default='', help='Select the installed ABI for extraction estimate; otherwise report per-ABI sizes without summing them as device storage')
    args = parser.parse_args()
    result = analyze(args.apk, label=args.label, source_revision=args.source_revision, build_variant=args.build_variant,
                     extract_native_libs=args.extract_native_libs, device_abi=args.device_abi)
    if args.compare:
        result['comparison'] = compare(json.loads(args.compare.read_text(encoding='utf-8')), result)
    if args.json_path:
        args.json_path.parent.mkdir(parents=True, exist_ok=True)
        args.json_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    if args.markdown:
        args.markdown.parent.mkdir(parents=True, exist_ok=True)
        args.markdown.write_text(markdown(result), encoding='utf-8')
    print(json.dumps({'apk_bytes': result['artifact']['apk_bytes'], 'sha256': result['artifact']['sha256'],
                      'elf_16k_failures': result['alignment']['elf_16k_failures']}, ensure_ascii=False))


if __name__ == '__main__':
    main()
