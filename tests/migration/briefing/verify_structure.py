"""检查工程交底迁移结构；不替代业务运行与数据库事务验收。"""
import argparse
import hashlib
from pathlib import Path
import re
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--root', type=Path, default=Path('.'))
parser.add_argument('--legacy-view', type=Path)
args = parser.parse_args()
root = args.root
base = root / 'pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering'
ui = root / 'yudao-ui/yudao-ui-admin-vue3/src'
fields = 'id code projectId name briefingType templateId templateSnapshot sourceSnapshot content fileUrl fileName fileSize fileChecksum status version generateTime publishTime approverUserId approveOpinion approveTime creatorUserId remark'.split()
def declared(path):
    return set(re.findall(r'private\s+[\w<>\[\]]+\s+(\w+)\s*;', path.read_text()))
entity = base / 'dal/dataobject/briefing/entity/BriefingEntityDO.java'
assert declared(entity) == set(fields + ['legacySourceId'])
response = base / 'controller/admin/briefing/entity/vo/BriefingEntityRespVO.java'
assert declared(response) == set(fields + ['createTime', 'legacySourceId'])
source = base / 'dal/mysql/briefing/entity/BriefingEntityImportSource.java'
assert declared(source) == set(fields + ['tenantId', 'deleted', 'creator', 'updater', 'createTime', 'updateTime'])
assert 'extends TenantBaseDO' not in source.read_text()
save = declared(base / 'controller/admin/briefing/entity/vo/BriefingEntitySaveReqVO.java')
assert not save.intersection({'legacySourceId', 'tenantId', 'deleted', 'status', 'approveTime', 'approverUserId', 'approveOpinion', 'generateTime', 'publishTime'})
controller = (base / 'controller/admin/briefing/entity/BriefingEntityController.java').read_text()
assert len(re.findall(r'@(?:Get|Post|Put|Delete)Mapping\(', controller)) == 10
assert controller.count('@PreAuthorize(') == 10
for operation in ['create', 'update', 'delete', 'query', 'generate', 'audit', 'publish']:
    assert f'pms:eng-briefing:{operation}' in controller
for path in base.rglob('*.java'):
    if '/briefing/entity/' not in path.as_posix():
        continue
    # 注释中保留来源说明；代码不得导入旧业务类或访问旧 Mapper。
    code = re.sub(r'/\*.*?\*/|//[^\n]*', '', path.read_text(), flags=re.S)
    assert not re.search(r'\b(?:BriefingDO|BriefingMapper|BriefingServiceImpl|BriefingService|BriefingSaveReqVO)\b', code), path
mapper_root = root / 'pms-module-engineering/src/main/resources/mapper/briefing/entity'
for path in mapper_root.glob('*.xml'):
    ET.fromstring(path.read_text())
    assert '${' not in path.read_text()
main_mapper = (mapper_root / 'BriefingEntityMapper.xml').read_text()
assert 'pms_eng_briefing' not in main_mapper
assert 'tenant_id = #{query.tenantId}' in main_mapper and "deleted = b'0'" in main_mapper
import_xml = (mapper_root / 'BriefingEntityImportMapper.xml').read_text()
assert 'FROM pms_eng_briefing' in import_xml
assert not re.search(r'(?:UPDATE|DELETE\s+FROM|INSERT\s+INTO)\s+pms_eng_briefing', import_xml, re.I)
assert '#{row.id}' in import_xml and '#{row.deleted}' in import_xml
for field in fields + ['tenantId', 'creator', 'updater', 'createTime', 'updateTime', 'deleted']:
    assert '#{row.' + field + '}' in import_xml, field
migration = (root / 'sql/migrations/V251__engineering_briefing_entity.sql').read_text()
assert not re.search(r'(?:ALTER|DROP|UPDATE|DELETE\s+FROM|INSERT\s+INTO)\s+`?pms_eng_briefing', migration, re.I)
assert 'CREATE TABLE `sol_engineering_briefing`' in migration
assert 'pms/engineering/briefing/entity/index' in migration
page = (ui / 'views/pms/engineering/briefing/entity/index.vue').read_text()
assert "'@/api/pms/engineering/briefing'" not in page
for handler in ['load', 'openCreate', 'openEdit', 'save', 'openDetail', 'confirmGenerate', 'confirmApprove', 'handlePublish', 'handleTerminate', 'remove']:
    assert f'const {handler} =' in page
legacy = args.legacy_view or ui / 'views/pms/engineering/briefing/index.vue'
if legacy.exists():
    raw = legacy.read_bytes()
    digest = hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()
    assert digest == '4975af095d59b1530af4f680d2ff65b04d78b87a', '旧页面已变化，需要重新审计后更新基线'
    # 模板一字不改，交互处理函数保留；新副本只切换 API、组件名和清空新建元数据。
    assert page.split('<script setup')[0] == raw.decode().split('<script setup')[0]
    before_handlers = set(re.findall(r'const (\w+) = (?:async )?\(', raw.decode()))
    after_handlers = set(re.findall(r'const (\w+) = (?:async )?\(', page))
    assert before_handlers == after_handlers
else:
    raise SystemExit('缺少旧页面，不能核验页面保留：请提供 --legacy-view')
print('通过字段、权限、路由、SQL边界、页面模板及交互保留结构检查')
