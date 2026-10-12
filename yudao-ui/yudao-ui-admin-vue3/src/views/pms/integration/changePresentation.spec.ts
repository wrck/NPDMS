import { describe, expect, it } from 'vitest'
import {
  dictionaryDataOriginalFields,
  deviceRelationshipOriginalFields,
  fieldChanges,
  formatFieldValue,
  projectContractOriginalFields
} from './changePresentation'

describe('integration field comparison', () => {
  it('compares relationship audit dates while retaining unchanged business fields and original source evidence', () => {
    const before = { id: '40', sourceDeviceSn: 'SN-A', targetDeviceSn: 'SN-B', creator: 'data_sync',
      createTime: 1669860340123, updateTime: 1669860340123, version: 0, effectiveTo: null }
    const change = { object: 'fb_shipment_barcode_relation', sourceKey: '20', action: 'UPDATED', before,
      after: { relationship: { ...before, createTime: 1669860240123, updateTime: 1669860240123, version: 1 },
        originalFields: { id: '20', sn1: 'SN-A', item1: 'A', sn2: 'SN-B', item2: 'B',
          contract: '', createtime: '2022-12-01 09:24:00.1230000', updatetime: null } } }
    const fields = fieldChanges(change)
    expect(fields.filter((row) => row.changed).map((row) => row.field)).toEqual(['createTime', 'updateTime', 'version'])
    expect(fields.find((row) => row.field === 'createTime')?.after).toMatch(/^2022-12-01 /)
    expect(deviceRelationshipOriginalFields(change)).toHaveLength(8)
    expect(deviceRelationshipOriginalFields(change).at(-1)?.value).toBe('NULL（空值）')
  })
  it('shows excluded self-relationship source evidence without implying a formal relationship', () => {
    const change = { object: 'fb_shipment_barcode_relation', sourceKey: '21', action: 'SKIPPED', before: {},
      after: { originalFields: { id: '21', sn1: 'SAME', sn2: 'SAME', item1: 'A', item2: 'A' } } }
    expect(fieldChanges(change)).toEqual([])
    expect(deviceRelationshipOriginalFields(change)).toHaveLength(5)
    expect(deviceRelationshipOriginalFields({ ...change, object: 'other' })).toEqual([])
  })
  it('shows formal dictionary fields independently of excluded source properties', () => {
    const change = {
      object: 'fnd_basic_data',
      sourceKey: '542',
      action: 'CREATED',
      before: {},
      after: {
        item: { id: null, sort: 0, label: '旧字典项', creator: 'system', updater: null },
        originalFields: { basicDataAttri1: '旧入口', effectiveTo: null }
      }
    }
    expect(fieldChanges(change).map((row) => row.field)).toEqual([
      'id',
      'sort',
      'label',
      'creator',
      'updater'
    ])
    expect(dictionaryDataOriginalFields(change)).toEqual([
      { field: 'basicDataAttri1', value: '旧入口' },
      { field: 'effectiveTo', value: 'NULL（空值）' }
    ])
  })
  it('excluded empty dictionary record has source evidence without implying a formal row', () => {
    const change = {
      object: 'fnd_basic_data',
      sourceKey: '114',
      action: 'SKIPPED',
      before: {},
      after: { originalFields: { id: '114', basicDataId: null } }
    }
    expect(fieldChanges(change)).toEqual([])
    expect(dictionaryDataOriginalFields(change)).toHaveLength(2)
  })
  it('preserves zero, false, null and empty text as different values', () => {
    expect([0, false, null, '', undefined].map(formatFieldValue)).toEqual([
      '0',
      'false',
      'NULL（空值）',
      '空字符串',
      '—'
    ])
  })

  it('includes new adapter fields and fields missing from the source', () => {
    const changes = fieldChanges({
      object: 'custom',
      sourceKey: '001',
      action: 'UPDATED',
      before: { retained: 'a', removed: 1 },
      after: { retained: 'a', futureField: false }
    })
    expect(changes).toEqual([
      { field: 'retained', before: 'a', after: 'a', changed: false },
      { field: 'removed', before: '1', after: '—', changed: true },
      { field: 'futureField', before: '—', after: 'false', changed: true }
    ])
  })

  it('retains nested values without turning them into object placeholders', () => {
    expect(formatFieldValue({ codes: ['001', '002'] })).toBe('{"codes":["001","002"]}')
  })
  it('compares generic target snapshots without reporting source evidence as changed fields', () => {
    expect(
      fieldChanges({
        object: 'PACKAGE',
        sourceKey: '1',
        action: 'UPDATED',
        before: { 'package.receiver_name': 'Alice' },
        after: {
          receiveName: 'Bob',
          _targets: [],
          _syncSourcePayload: { receiveName: 'Bob' },
          'package.receiver_name': 'Bob'
        }
      })
    ).toEqual([{ field: 'package.receiver_name', before: 'Alice', after: 'Bob', changed: true }])
  })
  it('labels preview-only reference IDs without hiding legitimate negative quantities', () => {
    const changes = fieldChanges({
      object: 'BARCODE',
      sourceKey: '1',
      action: 'CREATED',
      before: {},
      after: {
        _targets: [],
        _previewVirtualIds: true,
        'device.shipment_record_id': -1,
        'line.quantity': -2
      }
    })
    expect(changes[0].after).toBe('待生成（预览）')
    expect(changes[1].after).toBe('-2')
  })
  it('compares formal contract columns instead of falsely removing every unchanged field', () => {
    const change = {
      object: 'pm_project_contract',
      sourceKey: '100:200',
      action: 'UPDATED',
      before: {
        id: '30',
        creator: '1',
        updater: '1',
        status: 'ACTIVE',
        version: 0,
        effectiveTo: null
      },
      after: {
        relation: {
          id: '30',
          creator: '21',
          updater: null,
          status: 'ACTIVE',
          version: 1,
          effectiveTo: null
        },
        originalFields: { id: '100', updateBy: null, member_mergeBranchMark: '' }
      }
    }
    const fields = fieldChanges(change)
    expect(fields.filter((field) => field.changed).map((field) => field.field)).toEqual([
      'creator',
      'updater',
      'version'
    ])
    expect(fields.find((field) => field.field === 'id')?.after).toBe('30')
    expect(fields.find((field) => field.field === 'effectiveTo')?.after).toBe('NULL（空值）')
    expect(projectContractOriginalFields(change)).toEqual([
      { field: 'id', value: '100' },
      { field: 'updateBy', value: 'NULL（空值）' },
      { field: 'member_mergeBranchMark', value: '空字符串' }
    ])
  })
  it('presents known contract dates without interpreting IDs as dates or exposing another source payload', () => {
    const time = 1669860340123
    const fields = fieldChanges({
      object: 'pm_project_contract',
      sourceKey: '100:200',
      action: 'UNCHANGED',
      before: { createTime: time, id: '30' },
      after: { relation: { createTime: time, id: '30' } }
    })
    expect(fields[0].after).toMatch(/^2022-12-01 \d{2}:\d{2}:40\.123$/)
    expect(fields.every((field) => !field.changed)).toBe(true)
    expect(fields[1].after).toBe('30')
    expect(
      projectContractOriginalFields({
        object: 'other',
        sourceKey: '1',
        action: 'UPDATED',
        after: { relation: { id: 1 }, originalFields: { private: 'not part of this contract' } }
      })
    ).toEqual([])
  })
})
