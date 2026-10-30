"""交底领域迁移的源码结构检查；不替代 Spring、权限集成、SQL 或真实文件验收。"""
import argparse
import re
from pathlib import Path
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--root', type=Path, default=Path('.'))
root = parser.parse_args().root
java = root / 'pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering'
service = (java / 'service/briefing/entity/BriefingEntityServiceImpl.java').read_text()
mapper = (java / 'dal/mysql/briefing/entity/BriefingEntityMapper.java').read_text()
query = (java / 'dal/mysql/briefing/entity/query/BriefingEntityPageQuery.java').read_text()
imp = (java / 'service/briefing/entity/BriefingEntityImportService.java').read_text()
xml = (root / 'pms-module-engineering/src/main/resources/mapper/briefing/entity/BriefingEntityImportMapper.xml').read_text()
count = 0

def check(condition, message):
    global count
    count += 1
    if not condition:
        raise AssertionError(message)

for forbidden in ['/pms/briefing/files/', '102400L', 'setFileChecksum("auto-', 'setApproverUserId(request.getApproverUserId())']:
    check(forbidden not in service, f'仍有伪造或客户端身份写入：{forbidden}')
check('BRIEFING_DOCUMENT_GENERATION_NOT_CONNECTED' in service, '缺少生成不可用的明确失败')
check('generator.verify(artifact, access.actorId())' in service, '未核验生成文件')
check(service.index('generator.verify(artifact, access.actorId())') < service.index('update.setTemplateId(artifact.templateId())'), '文件核验晚于对象写入准备')
check('verifyDocument(current)' in service, '审核/发布未核验文件')
for op in ['requireEditable', 'requireDraft', 'requireGeneratable', '.generated(', '.reviewed(', '.published(', '.terminated(']:
    check(op in service, f'未经过聚合：{op}')
lock = service[service.index('    private BriefingEntityDO lock('):service.index('    private BriefingAggregate aggregate(')]
check(lock.index('access.lockWrite') < lock.index('selectForUpdate'), '交底先锁对象后锁项目')
check('row.getProjectId(), identity.getProjectId()' in lock, '缺少锁前后项目归属重验')
check('query.setVisibleProjectIds(access.visibleProjects())' in service, '分页缺少可信项目范围')
check('Set<Long> visibleProjectIds' in query, 'Query 未显式持有范围')
check('Objects.requireNonNull(query.getVisibleProjectIds()' in mapper, '空权限未拒绝')
check('query.getVisibleProjectIds().isEmpty()' in mapper, '空集合未返回空页')
check('.in(BriefingEntityDO::getProjectId, query.getVisibleProjectIds())' in mapper, '未在 SQL 前过滤范围')
check('.inIfPresent(' not in mapper, '空集合可能扩大查询')
check(imp.index('access.lockWrite') < imp.index('mapper.selectSourceForUpdate'), '承接锁顺序错误')
check('access.requireReadable(identity.getProjectId())' in imp, '缺少来源读取权限')
check('source.getProjectId(), identity.getProjectId()' in imp, '来源项目变化未阻断')
check('expected.equals(existing)' in imp, '丢失完整内容重试校验')
check('expected.equals(mapper.selectTargetForUpdate(query))' in imp, '丢失回读校验')
check(not re.search(r'(?:UPDATE|DELETE\s+FROM|INSERT\s+INTO)\s+pms_eng_briefing',xml,re.I), '旧表被写入')
check('${' not in xml, '出现 SQL 字符串拼接')
parsed = ET.fromstring(xml)
check({n.attrib['id'] for n in parsed.findall('select')} == {'selectSource','selectSourceForUpdate','selectTargetForUpdate'}, '迁移查询声明不完整')
check('FOR UPDATE' not in parsed.find("select[@id='selectSource']").text, '定位查询不应先锁旧行')
for node in parsed.findall('select'):
    check('tenant_id = #{query.tenantId}' in node.text and 'id = #{query.id}' in node.text,'查询缺少身份条件')
for path in (java/'domain/briefing').glob('*.java'):
    text = path.read_text()
    imports = re.findall(r'^import (.*);',text,re.M)
    check(all(i.startswith('java.') or i.startswith('cn.iocoder.yudao.module.pms.engineering.domain.briefing.') for i in imports), f'领域依赖向外泄漏：{path}')
print(f'PASS: {count} source-structure checks; not runtime verification')
