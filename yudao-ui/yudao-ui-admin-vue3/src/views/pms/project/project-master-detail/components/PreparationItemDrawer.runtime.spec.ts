import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import {
  findByTestId,
  mount,
  passthrough,
  type TestNode
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import Drawer from './PreparationItemDrawer.vue'
import type { PreparationItemVO, PreparationVO } from '@/api/pms/engineering/preparation'

const api = vi.hoisted(() => ({ patchItem: vi.fn(), getAssignmentCandidates: vi.fn() }))
vi.mock('@/api/pms/engineering/preparation', () => api)
vi.mock('@/api/pms/platform/file', () => ({}))
vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({ success: vi.fn(), info: vi.fn(), confirm: vi.fn().mockResolvedValue(true) })
}))
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => ({ value: false }) }))
vi.mock('@/components/PmsFileArtifact', () => ({
  PmsFileReferenceList: { render: () => null },
  PmsFileUploader: { render: () => null }
}))
const flush = async () => {
  for (let i = 0; i < 6; i++) {
    await nextTick()
    await Promise.resolve()
  }
}
const apps: { unmount(): void }[] = []
const make = async (actions = ['PATCH_ASSIGNEE_FIELDS']) => {
  const child = ref<any>()
  const wrapper = defineComponent({
    setup: () => () => h(Drawer, { projectVersion: 5, ref: child })
  })
  const drawerStub = defineComponent({
    inheritAttrs: false,
    setup:
      (_, { slots }) =>
      () =>
        h('section', [slots.default?.(), slots.footer?.()])
  })
  const components = Object.fromEntries(
    [
      'ElDescriptions',
      'ElDescriptionsItem',
      'ElSelect',
      'ElOption',
      'ElInput',
      'ElInputNumber',
      'ElSwitch',
      'ElRadioGroup',
      'ElRadio',
      'ElRadioButton'
    ].map((key) => [key, passthrough])
  )
  const mounted = mount(wrapper, {}, { ...components, ElDrawer: drawerStub })
  apps.push(mounted.app)
  const current = {
    preparationId: 10,
    projectId: 1,
    version: 2,
    inputVersion: 3,
    readinessVersion: 1
  } as PreparationVO
  const item = {
    itemId: 20,
    itemCode: 'CABINET',
    itemName: '机柜',
    version: 4,
    applicability: 'REQUIRED',
    confirmationStatus: 'PENDING',
    outsourced: false,
    allowedActions: actions,
    sources: [],
    form: {
      version: 1,
      schemaSnapshot: '{"fields":[]}',
      valueSnapshot: '{"siteCondition":"旧记录"}'
    }
  } as unknown as PreparationItemVO
  child.value.open(current, item)
  await flush()
  return {
    root: mounted.root,
    instance: child.value,
    field: (key: string) => findByTestId(mounted.root, `survey-${key}`)!
  }
}
const invoke = async (node: TestNode, name: string, value?: unknown) => {
  const fn = node.props?.[name] as (v?: unknown) => unknown
  await fn(value)
  await flush()
}
beforeEach(() => {
  vi.clearAllMocks()
  api.patchItem.mockResolvedValue({})
})
afterEach(() => {
  apps.splice(0).forEach((app) => app.unmount())
})

describe('现场工勘明确业务字段保存', () => {
  it('未填写显示待确认，否值原样进入typed payload而不双写旧JSON', async () => {
    const view = await make()
    expect(view.field('cabinetAvailable').props?.['model-value']).toBe('UNKNOWN')
    await invoke(view.field('cabinetAvailable'), 'onUpdate:modelValue', 'NO')
    expect(view.instance.isDirty()).toBe(true)
    await invoke(findByTestId(view.root, 'survey-item-save')!, 'onClick')
    const payload = api.patchItem.mock.calls[0][3]
    expect(payload.surveyResult).toEqual({ cabinetAvailable: false, cabinet: null })
    expect(payload).not.toHaveProperty('formValueSnapshot')
    expect(payload).not.toHaveProperty('siteResultCode')
    expect(payload.expectedProjectVersion).toBe(5)
  })
  it('失败保留填写，不伪报保存或关闭', async () => {
    api.patchItem.mockRejectedValue(new Error('version conflict'))
    const view = await make()
    await invoke(view.field('cabinet'), 'onUpdate:modelValue', '机柜空间不足')
    await expect(invoke(findByTestId(view.root, 'survey-item-save')!, 'onClick')).rejects.toThrow(
      'version conflict'
    )
    expect(view.instance.isDirty()).toBe(true)
    expect(view.field('cabinet').props?.['model-value']).toBe('机柜空间不足')
  })
  it('只读字段即使直接触发也不改变输入', async () => {
    const view = await make([])
    expect(view.field('cabinet').props?.disabled).toBe(true)
    await invoke(view.field('cabinet'), 'onUpdate:modelValue', '越权填写')
    expect(view.instance.isDirty()).toBe(false)
    expect(api.patchItem).not.toHaveBeenCalled()
    expect(findByTestId(view.root, 'survey-item-save')).toBeUndefined()
  })
})
