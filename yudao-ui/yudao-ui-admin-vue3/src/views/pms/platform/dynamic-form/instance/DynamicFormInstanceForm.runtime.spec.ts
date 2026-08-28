import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as DynamicFormApi from '@/api/pms/platform/dynamic-form'
import {
  buildInstanceRuntime,
  changedOrdinaryValues,
  reconcileInstancePatch,
  stableCommandIntent
} from '../components/dynamicFormRuntime'
import DynamicFormInstanceForm from './DynamicFormInstanceForm.vue'
import { mount, passthrough, textOf } from '../components/runtimeTestHarness'

vi.mock('@/api/pms/platform/dynamic-form', () => ({ getInstance: vi.fn(), patchInstance: vi.fn() }))
vi.mock('../components/registerDynamicFormComponents', () => ({
  registerDynamicFormComponents: vi.fn()
}))

const detail = () =>
  ({
    instanceId: 7,
    instanceCode: 'DFI-7',
    instanceName: '现场记录',
    templateId: 2,
    templateCode: 'T',
    templateName: '巡检',
    templateRevisionId: 20,
    templateRevisionNo: 3,
    engineCode: 'FORM_CREATE_ELEMENT_PLUS',
    designerVersion: '3.4.0',
    rendererVersion: '3.2.38',
    formConfJson: {},
    formRulesJson: [
      { type: 'input', field: 'zero' },
      { type: 'group', children: [{ type: 'PmsFileArtifact', field: 'evidence' }] }
    ],
    values: { zero: 0, enabled: false, cleared: null, list: [] },
    controlledFiles: { evidence: [] },
    instanceVersion: 4,
    createdBy: 1,
    allowedActions: ['PATCH_INSTANCE'],
    createTime: '',
    updateTime: ''
  }) as any

describe('F-PLT-002 frozen instance form', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    const values = new Map<string, string>()
    vi.stubGlobal('sessionStorage', {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => values.set(key, value),
      removeItem: (key: string) => values.delete(key)
    })
    vi.mocked(DynamicFormApi.getInstance).mockResolvedValue(detail())
  })

  it('injects controlled fields recursively and keeps false, zero, null and empty arrays in a genuine partial patch', () => {
    const runtime = buildInstanceRuntime(detail().formRulesJson, {
      instanceId: 7,
      templateRevisionId: 20,
      controlledFiles: {},
      allowedActions: ['PATCH_INSTANCE']
    })
    expect(runtime.controlled.has('evidence')).toBe(true)
    expect((runtime.rules[1].children as any[])[0].props).toMatchObject({
      instanceId: 7,
      fieldKey: 'evidence'
    })
    const changed = changedOrdinaryValues(
      { zero: 0, enabled: false, cleared: null, list: [] },
      { zero: 1, enabled: true, cleared: 'x', list: [1] },
      new Set(['zero', 'enabled', 'cleared', 'list'])
    )
    expect(changed).toEqual({ zero: 0, enabled: false, cleared: null, list: [] })
  })

  it('retains the same key after an unknown response and rotates it for a changed intent', () => {
    const first = stableCommandIntent('instance-create', { revision: 2, name: 'A' })
    const retry = stableCommandIntent('instance-create', { revision: 2, name: 'A' })
    const changed = stableCommandIntent('instance-create', { revision: 2, name: 'B' })
    expect(retry.key).toBe(first.key)
    expect(changed.key).not.toBe(first.key)
  })

  it('reloads the authoritative CAS version while preserving values that were not committed', () => {
    const pending = { zero: 0, enabled: false }
    expect(reconcileInstancePatch({ zero: 0, enabled: false }, pending).committed).toBe(true)
    expect(reconcileInstancePatch({ zero: 1, enabled: true }, pending)).toEqual({
      committed: false,
      values: { zero: 0, enabled: false }
    })
    expect(
      reconcileInstancePatch({ nested: { a: 1, b: 2 } }, { nested: { b: 2, a: 1 } }).committed
    ).toBe(true)
  })

  it('mounts the authoritative readonly/write branch and identifies the frozen revision', async () => {
    const FormCreate = defineComponent({ setup: () => () => h('div', 'rendered-form') })
    const mounted = mount(
      DynamicFormInstanceForm,
      { modelValue: true, instanceId: 7 },
      {
        ElDrawer: passthrough,
        ElAlert: passthrough,
        ElEmpty: passthrough,
        'form-create': FormCreate
      }
    )
    await Promise.resolve()
    await nextTick()
    expect(textOf(mounted.root)).toContain('冻结修订 3')
    expect(textOf(mounted.root)).toContain('保存填写值')
    mounted.app.unmount()
  })
})
