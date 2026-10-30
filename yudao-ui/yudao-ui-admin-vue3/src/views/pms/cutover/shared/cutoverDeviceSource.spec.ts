import { describe, expect, it } from 'vitest'
import type { CutoverChecklistItem } from '@/api/pms/cutover'
import { needsDeviceListFields, rebuildTaskDeviceAnswer } from './cutoverDeviceSource'

const item = { stableItemKey: 'SURVEY_DEVICE_LOCATION_PLAN', applicable: true, workModeCode: 'DIRECT',
  interfaceSchemaSnapshot: JSON.stringify({ columns: [{ code: 'serialNumber' }, { code: 'location' }] }) } as CutoverChecklistItem
const devices = [
  { deviceId: '9007199254740993', serialNumber: 'SN-A', projectAssignmentVersion: '1' },
  { deviceId: '9007199254740994', serialNumber: 'SN-B', projectAssignmentVersion: '2' }
]

describe('task device source for mutable survey answers', () => {
  it('seeds blank deployment and baseline tables in task device order', () => {
    for (const key of ['SURVEY_DEVICE_LOCATION_PLAN', 'SURVEY_PERFORMANCE_BASELINE']) {
      expect(JSON.parse(rebuildTaskDeviceAnswer({ item: { ...item, stableItemKey: key }, devices })!))
        .toEqual([{ serialNumber: 'SN-A' }, { serialNumber: 'SN-B' }])
    }
  })

  it('removes unmatched rows, preserves matched facts and does not mutate input or duplicate devices', () => {
    const rows = [{ serialNumber: 'FOREIGN', location: '删除的设备' }, { serialNumber: 'SN-B', location: 'B机柜', deviceName: '防火墙', cpuUsage: 0 },
      { serialNumber: 'SN-B', location: '重复行' }, {}]
    const value = JSON.stringify({ rows, note: '表格备注' })
    const result = rebuildTaskDeviceAnswer({ item, devices, value })!
    expect(JSON.parse(result)).toEqual({ note: '表格备注', rows: [{ serialNumber: 'SN-A' }, rows[1]] })
    expect(rows[0].serialNumber).toBe('FOREIGN')
    expect(rebuildTaskDeviceAnswer({ item, devices, value: result })).toBe(result)
  })

  it('keeps non-device schemas and manual evidence intact, and empty scope never expands to arbitrary devices', () => {
    const value = '[{"serialNumber":"FOREIGN"}]'
    expect(rebuildTaskDeviceAnswer({ item: { ...item, stableItemKey: 'CUSTOM' }, devices, value })).toBe(value)
    expect(rebuildTaskDeviceAnswer({ item: { ...item, currentResult: { resultSourceCode: 'MANUAL' } as any }, devices, value })).toBe(value)
    expect(rebuildTaskDeviceAnswer({ item, devices: [], value })).toBe('[]')
  })

  it('fills only columns present in the target schema and keeps entered facts and numeric zero', () => {
    const target = { ...item, interfaceSchemaSnapshot: JSON.stringify({ columns: [
      { code: 'serialNumber' }, { code: 'deviceName' }, { code: 'location' }, { code: 'deviceType' },
      { code: 'productCode' }, { code: 'productModel' }, { code: 'currentSoftwareVersion' }, { code: 'cpuUsage' }
    ] }) }
    const deviceList = { [devices[0].deviceId]: { deviceId: devices[0].deviceId, sn: 'SN-A', name: '设备A',
      productCode: 'CODE-A', productModel: 'FW-1000', conpVersion: 'R-11', conpType: 'FW',
      locationSnapshot: '{"fallbackLocation":"A机房机柜1","siteId":1}' } } as any
    const result = JSON.parse(rebuildTaskDeviceAnswer({ item: target, devices: [devices[0]], deviceList,
      value: '[{"serialNumber":"SN-A","deviceName":"用户名称","location":" ","cpuUsage":0}]' })!)
    expect(result).toEqual([{ serialNumber: 'SN-A', deviceName: '用户名称', location: 'A机房机柜1', cpuUsage: 0,
      productCode: 'CODE-A', productModel: 'FW-1000', currentSoftwareVersion: 'R-11' }])
    expect(result[0]).not.toHaveProperty('deviceType')
    expect(result[0]).not.toHaveProperty('productName')
  })

  it('seeds device names for empty interface and IP tables without guessing interfaces or changing existing rows', () => {
    const deviceList = { [devices[0].deviceId]: { deviceId: devices[0].deviceId, sn: 'SN-A', name: '主设备' } } as any
    for (const [stableItemKey, field] of [['SURVEY_INTERFACE_INTERCONNECT_PLAN', 'localDevice'], ['SURVEY_IP_VLAN_PLAN', 'deviceName']]) {
      const target = { ...item, stableItemKey, interfaceSchemaSnapshot: JSON.stringify({ columns: [{ code: field }, { code: 'interface' }] }) }
      expect(JSON.parse(rebuildTaskDeviceAnswer({ item: target, devices, deviceList, value: '[{}]' })!)).toEqual([{ [field]: '主设备' }])
      const existing = JSON.stringify([{ [field]: '已填设备', interface: 'GE1' }])
      expect(rebuildTaskDeviceAnswer({ item: target, devices, deviceList, value: existing })).toBe(existing)
    }
  })

  it('rejects mismatched identity and leaves unavailable source fields blank', () => {
    const target = { ...item, interfaceSchemaSnapshot: '{"columns":[{"code":"serialNumber"},{"code":"deviceName"},{"code":"location"}]}' }
    for (const wrong of [{ deviceId: devices[0].deviceId, sn: 'WRONG', name: '错误名称' },
      { deviceId: devices[1].deviceId, sn: 'SN-A', name: '错误名称' }]) {
      expect(JSON.parse(rebuildTaskDeviceAnswer({ item: target, devices: [devices[0]],
        deviceList: { [devices[0].deviceId]: wrong } as any })!)).toEqual([{ serialNumber: 'SN-A' }])
    }
  })

  it('does not request device facts when target fields are already filled or have no source mapping', () => {
    expect(needsDeviceListFields({ item, value: '[{"serialNumber":"SN-A","location":"已填写位置"}]' })).toBe(false)
    expect(needsDeviceListFields({ item, value: '[{"serialNumber":"SN-A"}]' })).toBe(true)
    expect(needsDeviceListFields({ item: { ...item, interfaceSchemaSnapshot: '{"columns":[{"code":"serialNumber"},{"code":"cpuUsage"}]}' }, value: '[{"serialNumber":"SN-A"}]' })).toBe(false)
  })
})
