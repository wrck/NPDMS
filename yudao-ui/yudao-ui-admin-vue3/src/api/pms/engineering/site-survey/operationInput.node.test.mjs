import test from 'node:test'
import assert from 'node:assert/strict'
import { siteSurveyOperationInput } from './operationInput.ts'

test('受控工勘输入只移除查询投影，保留原写入字段和未知字段供服务端校验', () => {
  const source = { projectId: 9, id: 11, code: 'SURVEY', name: '工勘', version: 2,
    execution: { task: { id: 1 } }, addressId: 10, addressVersion: 2, siteId: 20, siteVersion: 3,
    siteLocationId: 30, siteLocationVersion: 4, locationResolutionStatus: 'RESOLVED',
    addressSnapshot: '{}', locationSnapshot: '{}', outsourceRequestId: 40, fieldBindings: { p: 'powerSupply' },
    fieldCatalog: [], createTime: 'now', businessValues: { tenantId: '原业务字段' }, extensionValues: { detail: ['a', 'b'] },
    locationMaintenance: { fallbackLocation: 'site' }, unexpected: true }
  const before = JSON.stringify(source)
  const result = siteSurveyOperationInput(source)
  assert.deepEqual(result, { projectId: 9, id: 11, code: 'SURVEY', name: '工勘', version: 2,
    businessValues: source.businessValues, extensionValues: source.extensionValues,
    locationMaintenance: source.locationMaintenance, unexpected: true })
  assert.equal(JSON.stringify(source), before)
  assert.notEqual(result, source)
})

test('创建与更新不补造实体或并发版本', () => {
  const source = { projectId: 9, code: 'SURVEY', name: '工勘' }
  assert.deepEqual(siteSurveyOperationInput(source), source)
  assert.equal(Object.hasOwn(siteSurveyOperationInput(source), 'id'), false)
  assert.equal(Object.hasOwn(siteSurveyOperationInput(source), 'version'), false)
})
