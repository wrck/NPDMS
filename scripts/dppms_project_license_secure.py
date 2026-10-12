"""Controlled license-code capture: encrypt in memory before creating any artifact.

The ordinary detail snapshot deliberately never reads licenseCode. This adapter
reads only IDs already present in that audited snapshot, on the same read-only
transaction, and returns AES-256-GCM envelopes. It never writes to either database.
"""
from __future__ import annotations
import base64
import hashlib
import hmac
import os
import re
import secrets
import json
from pathlib import Path
from Crypto.Cipher import AES

KEY_ENV = 'PMS_LICENSE_HISTORY_MASTER_KEY'
KEY_ID_ENV = 'PMS_LICENSE_HISTORY_KEY_ID'


def encrypt_code(code: str, tenant_id: int, source_project_key: str, source_key: str,
                 master_key: bytes, key_id: str) -> dict:
    if (len(master_key) != 32 or isinstance(tenant_id, bool) or tenant_id <= 0
            or not re.fullmatch(r'[1-9][0-9]*', source_project_key)
            or not re.fullmatch(r'[1-9][0-9]*', source_key)
            or not re.fullmatch(r'[A-Za-z0-9._-]{1,64}', key_id)
            or not isinstance(code, str) or not code or len(code) > 256):
        raise ValueError('LICENSE_CAPTURE_INPUT_INVALID')
    aad = f'DPPMS-LICENSE|{tenant_id}|{source_project_key}|{source_key}'.encode('utf8')
    plaintext = bytearray(code.encode('utf8'))
    try:
        nonce = secrets.token_bytes(12)
        cipher = AES.new(master_key, AES.MODE_GCM, nonce=nonce, mac_len=16)
        cipher.update(aad)
        ciphertext, tag = cipher.encrypt_and_digest(plaintext)
        code_mac = hmac.new(master_key, aad + b'\0' + plaintext, hashlib.sha256).hexdigest()
        return dict(sourceKey=source_key, ciphertext=base64.b64encode(nonce + ciphertext + tag).decode('ascii'),
                    keyId=key_id, codeMac=code_mac)
    finally:
        plaintext[:] = b'\0' * len(plaintext)


def capture_envelopes(connection, license_rows, tenant_id: int, source_project_key: str) -> list[dict]:
    # Check all eight original columns, including the separately protected code; unrelated views are not replaced.
    schema_path = Path(__file__).resolve().parents[1] / 'pms-module-project/src/main/resources/projectlegacy/dppms-original-source-schema.json'
    expected_columns = json.loads(schema_path.read_text(encoding='utf8'))['tables']['pm_project_license_info_from_license']['columns']
    with connection.cursor() as cursor:
        cursor.execute('SHOW FULL COLUMNS FROM pm_project_license_info_from_license')
        observed_columns = list(cursor.fetchall())
    if len(observed_columns) != len(expected_columns) or any(
            (actual['Field'], actual['Type'], actual['Null'], actual['Key']) !=
            (expected['name'], expected['type'], expected['nullable'], expected['key'])
            for actual, expected in zip(observed_columns, expected_columns)):
        raise ValueError('LICENSE_CAPTURE_SCHEMA_DRIFT')
    ids = [int(row['id']) for row in license_rows]
    if len(set(ids)) != len(ids):
        raise ValueError('LICENSE_CAPTURE_SOURCE_DUPLICATE')
    if not ids:
        return []
    try:
        key = base64.b64decode(os.environ[KEY_ENV], validate=True)
        key_id = os.environ[KEY_ID_ENV]
    except (KeyError, ValueError):
        raise ValueError('LICENSE_CAPTURE_KEY_UNAVAILABLE') from None
    if len(key) != 32:
        raise ValueError('LICENSE_CAPTURE_KEY_INVALID')
    result = []
    expected_by_id = {int(row['id']): row for row in license_rows}
    business_fields = ('sn', 'specModel', 'contract', 'contractType', 'item', 'status')
    for offset in range(0, len(ids), 500):
        part = ids[offset:offset + 500]
        with connection.cursor() as cursor:
            cursor.execute('SELECT id,licenseCode,sn,specModel,contract,contractType,item,status FROM pm_project_license_info_from_license WHERE id IN ('
                           + ','.join(['%s'] * len(part)) + ') ORDER BY id', part)
            observed = list(cursor.fetchall())
        try:
            if {row['id'] for row in observed} != set(part):
                raise ValueError('LICENSE_CAPTURE_SOURCE_MISSING')
            for row in observed:
                expected = expected_by_id[row['id']]
                if any((None if row[field] is None else str(row[field])) != expected[field] for field in business_fields):
                    raise ValueError('LICENSE_CAPTURE_SOURCE_CHANGED')
                result.append(encrypt_code(row['licenseCode'], tenant_id, source_project_key, str(row['id']), key, key_id))
        finally:
            for row in observed:
                row['licenseCode'] = None
    return sorted(result, key=lambda item: int(item['sourceKey']))
