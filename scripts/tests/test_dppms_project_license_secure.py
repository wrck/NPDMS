import base64
import hashlib
import hmac
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
from Crypto.Cipher import AES

spec=importlib.util.spec_from_file_location('license_capture',Path(__file__).resolve().parents[1]/'dppms_project_license_secure.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

class LicenseCaptureTest(unittest.TestCase):
    def test_codes_including_whitespace_are_authenticated_without_plaintext_artifacts(self):
        key=bytes(range(32))
        for original in ['  synthetic-license\n', '合成许可']:
            envelope=module.encrypt_code(original,1,'101','201',key,'TEST-V1')
            self.assertEqual(set(envelope),{'sourceKey','ciphertext','keyId','codeMac'})
            payload=base64.b64decode(envelope['ciphertext'])
            cipher=AES.new(key,AES.MODE_GCM,nonce=payload[:12],mac_len=16)
            aad=b'DPPMS-LICENSE|1|101|201';cipher.update(aad)
            self.assertEqual(cipher.decrypt_and_verify(payload[12:-16],payload[-16:]).decode('utf8'),original)
            self.assertEqual(envelope['codeMac'],hmac.new(key,aad+b'\0'+original.encode('utf8'),hashlib.sha256).hexdigest())
            if original: self.assertNotIn(original,envelope['ciphertext'])
            other=module.encrypt_code(original,1,'101','201',key,'TEST-V1')
            self.assertNotEqual(envelope['ciphertext'],other['ciphertext'])
            self.assertEqual(envelope['codeMac'],other['codeMac'])

    def test_tenant_project_source_binding_rejects_substitution(self):
        key=bytes(range(32));envelope=module.encrypt_code('synthetic',1,'101','201',key,'TEST-V1')
        payload=base64.b64decode(envelope['ciphertext'])
        for aad in [b'DPPMS-LICENSE|2|101|201',b'DPPMS-LICENSE|1|102|201',b'DPPMS-LICENSE|1|101|202']:
            cipher=AES.new(key,AES.MODE_GCM,nonce=payload[:12],mac_len=16);cipher.update(aad)
            with self.assertRaises(ValueError): cipher.decrypt_and_verify(payload[12:-16],payload[-16:])

    def test_original_null_is_not_misrepresented_as_empty(self):
        for original in [None, '']:
            with self.assertRaisesRegex(ValueError,'LICENSE_CAPTURE_INPUT_INVALID'):
                module.encrypt_code(original,1,'101','201',bytes(range(32)),'TEST-V1')

    def test_capture_checks_all_source_columns_and_business_values_before_encryption(self):
        import json
        schema=Path(__file__).resolve().parents[2]/'pms-module-project/src/main/resources/projectlegacy/dppms-original-source-schema.json'
        columns=json.loads(schema.read_text(encoding='utf8'))['tables']['pm_project_license_info_from_license']['columns']
        observed=[dict(Field=c['name'],Type=c['type'],Null=c['nullable'],Key=c['key']) for c in columns]
        original=dict(id='201',sn=None,specModel='  MODEL  ',contract='CONTRACT',contractType='OLD',item='PRODUCT',status='原状态')
        source_row={**original,'id':201,'licenseCode':'SYNTHETIC-LICENSE'}
        class Cursor:
            def __init__(self): self.query=''
            def __enter__(self): return self
            def __exit__(self,*_args): pass
            def execute(self,sql,params=None): self.query=sql
            def fetchall(self): return observed if self.query.startswith('SHOW') else [source_row]
        class Connection:
            def cursor(self):return Cursor()
        with patch.dict(module.os.environ,{module.KEY_ENV:base64.b64encode(bytes(range(32))).decode(),module.KEY_ID_ENV:'TEST-V1'}):
            envelopes=module.capture_envelopes(Connection(),[original],1,'1')
            self.assertEqual(len(envelopes),1);self.assertIsNone(source_row['licenseCode'])
            source_row['specModel']='CHANGED';source_row['licenseCode']='SYNTHETIC-LICENSE'
            with self.assertRaisesRegex(ValueError,'LICENSE_CAPTURE_SOURCE_CHANGED'):
                module.capture_envelopes(Connection(),[original],1,'1')
            self.assertIsNone(source_row['licenseCode'])
            observed.pop()
            with self.assertRaisesRegex(ValueError,'LICENSE_CAPTURE_SCHEMA_DRIFT'):
                module.capture_envelopes(Connection(),[original],1,'1')

if __name__=='__main__': unittest.main()
