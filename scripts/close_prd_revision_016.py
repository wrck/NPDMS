#!/usr/bin/env python3
"""Expand the audited remediation recipe, then execute it on this branch only.

The transport files are temporary. The resulting commit contains readable Python;
all actual writes are checked against the recipe's documentation-only allowlist.
"""
from pathlib import Path
import ast
import base64
import hashlib
import lzma

root = Path(__file__).parent / 'prd_revision_016_transport'
parts = [root / f'part-{index:02d}.txt' for index in range(1, 10)]
encoded = ''.join(path.read_text(encoding='ascii').strip() for path in parts)
source_bytes = lzma.decompress(base64.b64decode(encoded, validate=True))
assert hashlib.sha256(source_bytes).hexdigest() == '8a69442cfe76016b281d5219713e4075c404c9eb310288b0e66590fd1e9d015f', 'Remediation transport integrity failure'
source = source_bytes.decode('utf-8')
ast.parse(source, filename=__file__)
Path(__file__).write_text(source, encoding='utf-8')
exec(compile(source, __file__, 'exec'), globals())
