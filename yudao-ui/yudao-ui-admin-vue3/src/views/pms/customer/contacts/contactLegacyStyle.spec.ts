import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
const legacy = read('../../project/customer-contact/index.vue')
const current = read('./index.vue')
const columns = (source: string) => [...source.matchAll(/<el-table-column\b[^>]*>/g)].map(([tag]) => ({
  label: /\blabel="([^"]+)"/.exec(tag)?.[1],
  width: /\swidth="([^"]+)"/.exec(tag)?.[1],
  minWidth: /\bmin-width="([^"]+)"/.exec(tag)?.[1]
}))
const tables = (source: string) => [...source.matchAll(/<el-table(?=\s)[\s\S]*?<\/el-table>/g)].map(([table]) => table)
const mainTable = (source: string) => {
  const table = tables(source)[0]
  if (!table) throw new Error('The contact listing table is missing')
  return table
}

describe('contact workbench retains the original page design', () => {
  it('keeps the original table column order and sizing', () => {
    const listing = columns(mainTable(current))
    expect(listing.filter(column => column.label !== '客户联系人角色')).toEqual(columns(mainTable(legacy)))
    expect(listing.find(column => column.label === '客户联系人角色')).toEqual({
      label: '客户联系人角色', width: undefined, minWidth: '140'
    })
  })
  it('keeps the source-picker columns separate from the main listing', () => {
    const picker = tables(current).find(table => table.includes(':data="sourceOptions"'))
    expect(picker).toBeDefined()
    expect(columns(picker!)).toEqual([
      { label: undefined, width: '48', minWidth: undefined },
      { label: '姓名', width: undefined, minWidth: '110' },
      { label: '部门', width: undefined, minWidth: '120' },
      { label: '职务', width: undefined, minWidth: '120' },
      { label: '手机', width: undefined, minWidth: '130' },
      { label: '邮箱', width: undefined, minWidth: '180' }
    ])
    expect(picker).toContain('type="selection"')
    expect(picker).toContain('@selection-change="sourceSelection = $event"')
  })
  it('retains inline queries and left-aligned form labels without a new visual shell', () => {
    expect(current).toContain('inline class="-mb-15px"')
    expect(current).toContain('label-width="100px"')
    expect(current).toContain('class="!w-220px"')
    expect(current).toContain('class="!w-160px"')
    expect(current).not.toContain('label-position="top"')
    expect(current).not.toContain('<h3>')
    expect(current).not.toContain('contacts-workbench')
    expect(current).not.toContain('customer-summary')
  })
})
