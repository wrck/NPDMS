import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import template from './demo-template.json'
import type { JsonObject } from '@/api/pms/platform/dynamic-form'
import {
  decodeDynamicForm,
  encodeDynamicForm
} from '@/views/pms/platform/dynamic-form/components/dynamicFormCodec'
import {
  buildInstanceRuntime,
  changedOrdinaryValues
} from '@/views/pms/platform/dynamic-form/components/dynamicFormRuntime'

const core = [
  'PROJECT_BACKGROUND',
  'PROJECT_OBJECTIVE',
  'NETWORK_TOPOLOGY',
  'TRANSMISSION_REQUIREMENT',
  'TRAFFIC_REQUIREMENT',
  'BUSINESS_REQUIREMENT',
  'IP_PLANNING',
  'REDUNDANCY_REQUIREMENT',
  'SECURITY_PROTECTION',
  'OPERATIONS_REQUIREMENT',
  'LOGGING_REQUIREMENT'
]
const rules = template.formRulesJson as JsonObject[]
const ruleFor = (field: string) => rules.find((rule) => rule.field === field)!

describe('PRE-04 Demo V2 form content', () => {
  it('preserves the existing core Editor, required fields and optional controlled attachment contract', () => {
    expect(rules.filter((rule) => rule.type === 'Editor').map((rule) => rule.field)).toEqual(core)
    expect(
      rules.filter((rule) => rule.type === 'PmsFileArtifact').map((rule) => rule.field)
    ).toEqual(core.map((field) => `${field}__ATTACHMENTS`))
    const required = rules.filter((rule) =>
      (rule.validate as JsonObject[] | undefined)?.some(
        (validation) => validation.required === true
      )
    )
    expect(required.map((rule) => rule.field)).toEqual(core.slice(0, 3))
    expect(new Set(rules.map((rule) => rule.field)).size).toBe(rules.length)
    expect(new Set(core.map((field) => (ruleFor(field).props as JsonObject).editorId)).size).toBe(
      11
    )
  })

  it('round-trips every rule, including nested business rows, through the actual shared codec', () => {
    const decoded = decodeDynamicForm(template.formConfJson, rules)
    const encoded = encodeDynamicForm(
      ref({ getOption: () => decoded.option, getRule: () => decoded.rule })
    )
    expect(encoded).toEqual(template)
  })

  it('keeps Demo choices and all eight business columns without inventing numeric units or importance levels', () => {
    expect(
      (ruleFor('TRANSMISSION_CURRENT_OPTIONS').options as JsonObject[]).map((item) => item.value)
    ).toEqual(['IPv6', '分片', 'MTU', 'Jumbo', '隧道'])
    expect(
      (ruleFor('OPERATIONS_MANAGEMENT_OPTIONS').options as JsonObject[]).map((item) => item.value)
    ).toEqual(['带内管理', '带外管理', 'SNMP', 'UMC', '第三平台', '堡垒机'])
    const rows = (ruleFor('BUSINESS_DEVICE_DETAILS').props as JsonObject).rule as JsonObject[]
    expect(rows.map((rule) => rule.title)).toEqual([
      '设备名称',
      '序列号',
      '承载业务名称',
      '业务网段',
      '业务重要等级',
      '出入接口',
      '客户侧业务负责人',
      '备注'
    ])
    expect(rows.every((rule) => rule.type === 'input')).toBe(true)
    for (const field of ['TRAFFIC_NEW_CONNECTIONS', 'TRAFFIC_CONCURRENCY', 'TRAFFIC_THROUGHPUT']) {
      expect(ruleFor(field).type).toBe('input')
      expect(ruleFor(field).value).toBeUndefined()
    }
  })

  it('isolates runtime file context and excludes forged attachment values from ordinary PATCH', () => {
    const before = structuredClone(template)
    const runtime = buildInstanceRuntime(rules, {
      instanceId: 123,
      templateRevisionId: 456,
      controlledFiles: {},
      allowedActions: ['PATCH_INSTANCE']
    })
    expect(runtime.controlled.size).toBe(11)
    expect(runtime.ordinary.size).toBe(19)
    expect(
      runtime.rules.find((rule) => rule.field === 'NETWORK_TOPOLOGY__ATTACHMENTS')!
        .props as JsonObject
    ).toMatchObject({
      instanceId: 123,
      templateRevisionId: 456,
      fieldKey: 'NETWORK_TOPOLOGY__ATTACHMENTS'
    })
    const result = changedOrdinaryValues(
      {
        TRAFFIC_NEW_CONNECTIONS: '0',
        TRANSMISSION_CURRENT_OPTIONS: [],
        IP_PUBLIC_RESOURCES: '',
        BUSINESS_DEVICE_DETAILS: [{ deviceName: '设备一', serialNumber: '00001', remark: '' }],
        NETWORK_TOPOLOGY__ATTACHMENTS: ['forged-reference'],
        UNKNOWN: 'not-a-field'
      },
      { TRANSMISSION_CURRENT_OPTIONS: ['IPv6'], IP_PUBLIC_RESOURCES: '198.51.100.0/24' },
      runtime.ordinary
    )
    expect(result).toEqual({
      TRAFFIC_NEW_CONNECTIONS: '0',
      TRANSMISSION_CURRENT_OPTIONS: [],
      IP_PUBLIC_RESOURCES: '',
      BUSINESS_DEVICE_DETAILS: [{ deviceName: '设备一', serialNumber: '00001', remark: '' }]
    })
    expect(template).toEqual(before)
  })

  it('keeps clearing and deleting business rows as explicit changes without copying nested fields to the root', () => {
    const runtime = buildInstanceRuntime(rules, {
      instanceId: 1,
      templateRevisionId: 2,
      controlledFiles: {},
      allowedActions: []
    })
    expect(
      changedOrdinaryValues(
        { BUSINESS_DEVICE_DETAILS: [], IP_MANAGEMENT_RESOURCES: null },
        {
          BUSINESS_DEVICE_DETAILS: [{ deviceName: '设备一' }],
          IP_MANAGEMENT_RESOURCES: '192.0.2.1'
        },
        runtime.ordinary
      )
    ).toEqual({ BUSINESS_DEVICE_DETAILS: [], IP_MANAGEMENT_RESOURCES: null })
    expect(runtime.ordinary.has('deviceName')).toBe(false)
  })

  it('contains content only, without enabling submission, business operations or outbound requests', () => {
    expect(template.formConfJson.submitBtn).toBe(false)
    expect(template.formConfJson.resetBtn).toBe(false)
    const serialized = JSON.stringify(template)
    expect(serialized).not.toMatch(/https?:|\/api\/|password|token|onSubmit|"on"\s*:|"effect"\s*:/i)
    expect(serialized).not.toContain('工程交底书')
  })
})
