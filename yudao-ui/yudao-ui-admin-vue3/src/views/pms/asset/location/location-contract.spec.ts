import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

const source = (relativePath: string) =>
  readFileSync(new URL(relativePath, import.meta.url), 'utf8')

describe('CHG-PRD-2026-08-23-002 location UI contract', () => {
  it('keeps site independent from company and department', () => {
    const siteSource = source('./site/index.vue')
    assert.match(siteSource, /customerId/)
    assert.doesNotMatch(siteSource, /companyId|departmentId/)
  })

  it('uses exact area and department codes for service-office mapping', () => {
    const mappingSource = source('./area-department/index.vue')
    assert.match(mappingSource, /areaCode/)
    assert.match(mappingSource, /departmentCode/)
    assert.doesNotMatch(mappingSource, /administrativeDivisionCode|officeCode/)
  })

  it('captures country province city district and detail address', () => {
    const addressSource = source('./address/index.vue')
    for (const field of [
      'countryCode',
      'provinceCode',
      'cityCode',
      'districtCode',
      'detailAddress',
      'fullAddress'
    ])
      assert.match(addressSource, new RegExp(field))
  })

  it('exposes required department code maintenance', () => {
    const deptSource = source('../../../system/dept/DeptForm.vue')
    assert.match(deptSource, /formData\.code/)
    assert.match(deptSource, /部门编码不能为空/)
  })
})
