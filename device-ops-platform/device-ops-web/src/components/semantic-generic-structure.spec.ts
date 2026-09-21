import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import SemanticGenericSection from '@/components/SemanticGenericSection.vue'
import type { GenericSection } from '@/types/parser'

const base = { sectionIndex: 1, startLine: 1, endLine: 2, rawLines: [], warnings: [] }
const mountSection = (section: GenericSection) => mount(SemanticGenericSection, {
  props: { section }, global: { plugins: [ElementPlus] }
})

describe('generic semantic structures', () => {
  it('renders parent values, children and repeated keys', () => {
    const wrapper = mountSection({ ...base, type: 'keyValueTree', data: {}, entries: [
      { key: 'parent', value: 'root', startLine: 1, endLine: 2, children: [
        { key: 'state', value: 'up', startLine: 2, endLine: 2, children: [] },
        { key: 'state', value: 'down', startLine: 3, endLine: 3, children: [] }
      ] }
    ] })
    expect(wrapper.text()).toContain('parent')
    expect(wrapper.text()).toContain('root')
    expect(wrapper.text().match(/state/g)).toHaveLength(2)
  })

  it('keeps table column order and renders null cells in a scroll container', () => {
    const wrapper = mountSection({ ...base, type: 'table', columns: [
      { id: 'column1', label: 'Name', index: 0 }, { id: 'column2', label: 'State', index: 1 }
    ], rows: [{ rowIndex: 1, values: { column1: 'edge', column2: null } }], unparsedLines: [] })
    expect(wrapper.find('.semantic-generic-table__scroll').exists()).toBe(true)
    expect(wrapper.findAllComponents({ name: 'ElTableColumn' }).map(column => column.props('label')))
      .toEqual(['Name', 'State'])
    expect(wrapper.findComponent({ name: 'ElTable' }).props('data')[0].values)
      .toEqual({ column1: 'edge', column2: null })
  })

  it('keeps record and config details unmounted until expanded', async () => {
    const record = mountSection({ ...base, type: 'recordList', records: [{ recordIndex: 1,
      identity: 'eth0', startLine: 1, endLine: 2,
      entries: [{ key: 'address', value: '10.0.0.1', startLine: 2, endLine: 2, children: [] }], sections: [] }] })
    expect(record.text()).toContain('eth0')
    expect(record.text()).not.toContain('10.0.0.1')
    await record.find('.el-collapse-item__header').trigger('click')
    expect(record.text()).toContain('10.0.0.1')

    const config = mountSection({ ...base, type: 'configStanza', stanzas: [{ header: 'interface eth0',
      startLine: 1, endLine: 2, lines: [' address 10.0.0.1'] }] })
    expect(config.text()).toContain('interface eth0')
    expect(config.text()).not.toContain('address 10.0.0.1')
    await config.find('.el-collapse-item__header').trigger('click')
    expect(config.text()).toContain('address 10.0.0.1')
  })

  it('lazily renders canonical record sections with empty entries in source order', async () => {
    const record = mountSection({ ...base, type: 'recordList', endLine: 5, records: [{
      recordIndex: 1, identity: 'eth0', startLine: 1, endLine: 5, entries: [], sections: [
        { ...base, type: 'keyValue', startLine: 2, data: {}, entries: [
          { key: 'address', value: '10.0.0.1', startLine: 2, endLine: 2, children: [] }
        ] },
        { ...base, sectionIndex: 2, type: 'text', startLine: 3, endLine: 3,
          lines: [{ lineNumber: 3, value: 'link details' }] },
        { ...base, sectionIndex: 3, type: 'table', startLine: 4, endLine: 5,
          columns: [{ id: 'column1', label: 'Peer', index: 0 }],
          rows: [{ rowIndex: 1, values: { column1: 'edge-peer' } }], unparsedLines: [] }
      ]
    }] })
    expect(record.text()).toContain('eth0')
    expect(record.findAll('.generic-section')).toHaveLength(1)
    expect(record.find('.generic-kv').exists()).toBe(false)
    expect(record.find('.generic-section__text').exists()).toBe(false)
    expect(record.findComponent({ name: 'ElTable' }).exists()).toBe(false)

    await record.find('.el-collapse-item__header').trigger('click')
    await flushPromises()

    expect(record.findAll('.generic-section')).toHaveLength(4)
    expect(record.find('.generic-kv').text()).toContain('10.0.0.1')
    expect(record.find('.generic-section__text').text()).toBe('link details')
    expect(record.find('.semantic-generic-table__scroll').text()).toContain('edge-peer')
    expect(record.findAll('.generic-kv, .generic-section__text, .semantic-generic-table__scroll')
      .map(section => section.classes()[0]))
      .toEqual(['generic-kv', 'generic-section__text', 'semantic-generic-table__scroll'])

    await record.find('.el-collapse-item__header').trigger('click')
    expect(record.findAll('.generic-section')).toHaveLength(1)
    expect(record.find('.generic-kv').exists()).toBe(false)
    expect(record.find('.generic-section__text').exists()).toBe(false)
    expect(record.findComponent({ name: 'ElTable' }).exists()).toBe(false)
  })

  it.each(['keyValue', 'keyValueTree'] as const)(
    'renders canonical %s record sections without duplicating legacy entries', async (type) => {
      const entries = [{ key: 'address', value: '10.0.0.1', startLine: 2, endLine: 2, children: [] }]
      const record = mountSection({ ...base, type: 'recordList', records: [{
        recordIndex: 1, identity: 'eth0', startLine: 1, endLine: 2, entries,
        sections: [{ ...base, type, data: {}, entries }]
      }] })
      await record.find('.el-collapse-item__header').trigger('click')

      expect(record.findAll('.generic-section')).toHaveLength(2)
      expect(record.findAll('.generic-kv__row')).toHaveLength(1)
      expect(record.text().match(/10\.0\.0\.1/g)).toHaveLength(1)
    }
  )

  it('recursively expands nested record sections with independent local indexes', async () => {
    const record = mountSection({ ...base, type: 'recordList', endLine: 3, records: [{
      recordIndex: 1, identity: 'eth0', startLine: 1, endLine: 3, entries: [], sections: [
        { ...base, type: 'recordList', startLine: 2, endLine: 3, records: [{
          recordIndex: 1, identity: 'peer', startLine: 2, endLine: 3, entries: [], sections: [
            { ...base, type: 'text', startLine: 3, endLine: 3,
              lines: [{ lineNumber: 3, value: 'nested detail' }] }
          ]
        }] }
      ]
    }] })
    expect(record.findAll('.el-collapse-item__header')).toHaveLength(1)
    await record.find('.el-collapse-item__header').trigger('click')

    expect(record.findAll('.el-collapse-item__header')).toHaveLength(2)
    expect(record.text()).toContain('peer')
    expect(record.find('.generic-section__text').exists()).toBe(false)
    await record.findAll('.el-collapse-item__header')[1]!.trigger('click')
    expect(record.find('.generic-section__text').text()).toBe('nested detail')

    await record.find('.el-collapse-item__header').trigger('click')
    expect(record.findAll('.el-collapse-item__header')).toHaveLength(1)
    expect(record.find('.generic-section__text').exists()).toBe(false)
  })

  it('preserves list and text order and exposes warnings', () => {
    const list = mountSection({ ...base, type: 'list', warnings: [{ code: 'LIMIT_REACHED', lineNumber: 2 }],
      items: [{ itemIndex: 1, value: 'first', startLine: 1, endLine: 1 },
        { itemIndex: 2, value: 'second', startLine: 2, endLine: 2 }] })
    expect(list.text()).toContain('LIMIT_REACHED')
    expect(list.text().indexOf('first')).toBeLessThan(list.text().indexOf('second'))
    const text = mountSection({ ...base, type: 'text', lines: [
      { lineNumber: 1, value: 'alpha' }, { lineNumber: 2, value: 'omega' }
    ] })
    expect(text.text().indexOf('alpha')).toBeLessThan(text.text().indexOf('omega'))
  })
})
