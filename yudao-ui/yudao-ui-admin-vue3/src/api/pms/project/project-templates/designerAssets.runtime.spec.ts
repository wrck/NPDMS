import { beforeEach, expect, it, vi } from 'vitest'
import { stageFromDefinition } from './designerAssets'
import type { DefinitionKind, DefinitionRevision } from './definitions'

const get = vi.hoisted(() => vi.fn())
vi.mock('@/config/axios', () => ({ default: { get } }))

const revision = (
  id: number,
  definitionKind: DefinitionKind,
  payload: DefinitionRevision['payload']
): DefinitionRevision => ({
  id,
  definitionKind,
  definitionCode: `ASSET_${id}`,
  schemaVersion: 1,
  revisionNo: 1,
  revisionState: 'PUBLISHED',
  version: 1,
  payload,
  references: []
})
const stage = (): DefinitionRevision => ({
  ...revision(1, 'STAGE', {
    stageCode: 'S1',
    name: '工前准备',
    workBinding: 'binding',
    permissionPolicy: 'permission',
    completionRule: 'completion'
  }),
  references: [
    { referenceKey: 'binding', targetRevisionId: 2 },
    { referenceKey: 'permission', targetRevisionId: 3 },
    { referenceKey: 'completion', targetRevisionId: 4 }
  ]
})

beforeEach(() => {
  get.mockReset()
  const rows = new Map([
    [2, revision(2, 'WORK_BINDING', { bindingType: 'STAGE_NATIVE' })],
    [3, revision(3, 'PERMISSION_POLICY', { roles: ['PROJECT_MANAGER'] })],
    [
      4,
      revision(4, 'COMPLETION_RULE', {
        predicate: 'STAGE_NATIVE_STATUS',
        parameters: { requiredStatus: 'DONE' }
      })
    ]
  ])
  get.mockImplementation(({ url }: { url: string }) =>
    Promise.resolve(rows.get(Number(url.split('/').pop())))
  )
})

it.each(['S1', 'PREP_WORK', 'Discovery.v2', 'Phase:Delivery'])(
  'copies stage %s with an independent stable node identity and rule snapshot', async (code) => {
  const asset = stage()
  asset.payload.stageCode = code
  const originalAsset = JSON.stringify(asset)
  const first = await stageFromDefinition(asset)
  const firstKey = first.nodeKey
  first.code = 'S0'
  first.name = '工前补充准备'
  const second = await stageFromDefinition(asset)

  expect(firstKey).not.toBe(second.nodeKey)
  expect(firstKey).not.toBe(`stage:${code}`)
  expect(first.nodeKey).toBe(firstKey)
  expect(second).toMatchObject({ code, name: '工前准备' })
  const reopened = JSON.parse(JSON.stringify([first, second]))
  expect(reopened.map((node: { nodeKey: string }) => node.nodeKey)).toEqual([
    firstKey,
    second.nodeKey
  ])
  expect(new Set(reopened.map((node: { nodeKey: string }) => node.nodeKey)).size).toBe(2)
  expect(first.source).toEqual(second.source)
  first.permission.policySnapshot!.roles.push('OTHER_ROLE')
  first.completionRule!.expression.parameters.requiredStatus = 'CHANGED'
  expect(second.permission.policySnapshot!.roles).toEqual(['PROJECT_MANAGER'])
  expect(second.completionRule!.expression.parameters.requiredStatus).toBe('DONE')
  expect(JSON.stringify(asset)).toBe(originalAsset)
})

it('still rejects unpublished or incomplete stage assets before copying', async () => {
  await expect(stageFromDefinition({ ...stage(), revisionState: 'DRAFT' })).rejects.toThrow(
    '请选择已发布阶段定义'
  )
  await expect(stageFromDefinition({ ...stage(), references: [] })).rejects.toThrow(
    '阶段定义缺少绑定'
  )
  expect(get).not.toHaveBeenCalled()
})
