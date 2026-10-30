import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import next from './template-20261013.json'
import previous from './demo-template.json'
import responsive from './template-20261013-r5.json'
import { decodeDynamicForm, encodeDynamicForm } from '@/views/pms/platform/dynamic-form/components/dynamicFormCodec'
import { ref } from 'vue'

describe('approved requirement template revision', () => {
  it('round-trips the responsive revision and publishes its exact payload without rewriting R4', () => {
    const decoded = decodeDynamicForm(responsive.formConfJson, responsive.formRulesJson)
    expect(encodeDynamicForm(ref({ getOption: () => decoded.option, getRule: () => decoded.rule }))).toEqual(responsive)
    const sql = readFileSync(resolve('..', '..', 'sql', 'migrations', 'V466__requirement_analysis_responsive_template_revision.sql'), 'utf8')
    const payloads = [...sql.matchAll(/'((?:[^']|'')*)'/g)].map(match => match[1].replace(/''/g, "'"))
    expect(payloads.map(value => { try { return JSON.parse(value) } catch { return undefined } })).toContainEqual(responsive.formRulesJson)
    expect(sql).not.toMatch(/UPDATE\s+(?:proj_|sol_|plt_dynamic_form_template_revision)/i)
    expect(responsive.formRulesJson.filter(rule => ['TRANSMISSION_REQUIREMENT', 'IP_PLANNING', 'BUSINESS_REQUIREMENT', 'OPERATIONS_REQUIREMENT'].includes(rule.field || ''))).toEqual([])
    expect(next.formRulesJson.some(rule => rule.field === 'IP_PLANNING')).toBe(true)
  })
  it('round-trips the new table and opt-in style classes through the shared template codec', () => {
    const decoded = decodeDynamicForm(next.formConfJson, next.formRulesJson)
    const encoded = encodeDynamicForm(ref({ getOption: () => decoded.option, getRule: () => decoded.rule }))
    expect(encoded).toEqual(next)
  })
  it('declares the requested controls directly and keeps the previous template unchanged', () => {
    expect(next.formRulesJson.some(rule => rule.field === 'NETWORK_TOPOLOGY')).toBe(false)
    expect(next.formRulesJson.filter(rule => rule.type === 'PmsFileArtifact').map(rule => rule.field)).toEqual(['NETWORK_TOPOLOGY__ATTACHMENTS'])
    const business = next.formRulesJson.find(rule => rule.field === 'BUSINESS_DEVICE_DETAILS')!
    expect(business.type).toBe('RequirementBusinessTable')
    expect(business.props!.columns!.map(column => column.field)).toEqual(['deviceName', 'serialNumber', 'businessName', 'businessSubnet', 'businessImportance', 'interfaces', 'customerBusinessOwner', 'remark'])
    expect(previous.formRulesJson.some(rule => rule.field === 'NETWORK_TOPOLOGY' && rule.type === 'Editor')).toBe(true)
    expect(previous.formRulesJson.find(rule => rule.field === 'BUSINESS_DEVICE_DETAILS')!.type).toBe('group')
  })
  it('publishes the exact JSON payload and only advances the template publication pointer', () => {
    const sql = readFileSync(resolve('..', '..', 'sql', 'migrations', 'V465__requirement_analysis_template_revision.sql'), 'utf8')
    const payloads = [...sql.matchAll(/'((?:[^']|'')*)'/g)].map(match => match[1].replace(/''/g, "'"))
    expect(payloads.map(value => { try { return JSON.parse(value) } catch { return undefined } })).toContainEqual(next.formConfJson)
    expect(payloads.map(value => { try { return JSON.parse(value) } catch { return undefined } })).toContainEqual(next.formRulesJson)
    expect(sql).not.toMatch(/UPDATE\s+(?:proj_|sol_|plt_dynamic_form_template_revision)/i)
  })
})
