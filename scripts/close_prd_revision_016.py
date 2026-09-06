#!/usr/bin/env python3
"""Expand the integrity-checked documentation-only remediation source."""
from pathlib import Path
import ast
import base64
import hashlib
import lzma

root = Path(__file__).parent / 'prd_revision_016_transport'
encoded = ''.join((root / f'part-{index:02d}.txt').read_text(encoding='ascii').strip() for index in range(1,10))
source_bytes = lzma.decompress(base64.b64decode(encoded, validate=True))
assert hashlib.sha256(source_bytes).hexdigest() == '8a69442cfe76016b281d5219713e4075c404c9eb310288b0e66590fd1e9d015f'
source = source_bytes.decode('utf-8')
old = '        text = text.replace(OLD_BLOB, blob)'
new = "        text = text.replace(OLD_BLOB, blob)\n        if '> PRD Blob：' not in text:\n            text = text.replace('> 当前依据：', f'> PRD Blob：`{blob}`<br>\\n> 当前依据：', 1)"
assert source.count(old) == 1
source = source.replace(old,new)
ast.parse(source,filename=__file__)
Path(__file__).write_text(source,encoding='utf-8')
exec(compile(source,__file__,'exec'),globals())
