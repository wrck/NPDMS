import test from 'node:test'
import assert from 'node:assert/strict'
import { ruleUses, copyVersionRule } from './versionRuleModel.ts'

const document = () => ({
  rules: [{ key: 'ready', name: '就绪', kind: 'CONDITION', shared: true,
    expression: { predicate: 'CONSTANT', parameters: { value: true } } }],
  stages: [{ code: 'S1', name: '准备', nodeKey: 'stage:S1', execution: {
    operations: [{ ownerContext: 'SOL', entityType: 'SITE_SURVEY', permissionCode: 'pms:eng-site-survey:update',
      pre: { mode: 'RULE', ruleKey: 'ready' }, post: { mode: 'NONE' } }],
    subscriptions: [{ key: 'survey', scope: { mode: 'OBJECTS', objectIds: ['9007199254740993'] } }]
  } }], tasks: [], transitions: []
})
test('新操作RULE引用参与规则使用提示，NONE不产生引用', () => {
  const source = document()
  assert.deepEqual(ruleUses(source, 'ready'), ['阶段 准备（S1） · pms:eng-site-survey:update · 前置'])
  assert.deepEqual(ruleUses(source, 'missing'), [])
})
test('序列化重开与复制规则不丢失独立配置或大整数身份', () => {
  const source = document()
  const before = JSON.stringify(source.stages[0].execution)
  const reopened = JSON.parse(JSON.stringify(source))
  copyVersionRule(reopened, 'ready')
  assert.equal(JSON.stringify(reopened.stages[0].execution), before)
  assert.equal(reopened.stages[0].execution.subscriptions[0].scope.objectIds[0], '9007199254740993')
  assert.notEqual(reopened.rules[1].key, 'ready')
})
