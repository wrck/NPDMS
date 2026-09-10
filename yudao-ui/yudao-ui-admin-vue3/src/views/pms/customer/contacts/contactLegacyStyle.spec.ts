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

describe('contact workbench retains the original page design', () => {
  it('keeps the original table column order and sizing', () => {
    expect(columns(current).filter(column => column.label !== '客户联系人角色')).toEqual(columns(legacy))
    expect(columns(current).find(column => column.label === '客户联系人角色')).toEqual({
      label: '客户联系人角色', width: undefined, minWidth: '140'
    })
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
