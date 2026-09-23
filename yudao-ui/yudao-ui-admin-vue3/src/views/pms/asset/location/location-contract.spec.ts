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

  it('treats leaf-level division selection as complete for domestic addresses', () => {
    const addressSource = source('./address/index.vue')
    assert.match(addressSource, /请选择省市区/)
    assert.doesNotMatch(addressSource, /请选择完整的省市区/)
  })

  it('unifies saved location display as site name with full address and full location chain', () => {
    for (const relativePath of [
      '../../delivery-business/site-survey/index.vue',
      '../../engineering/site-survey/index.vue',
      '../../engineering/installation/index.vue'
    ]) {
      const usageSource = source(relativePath)
      assert.match(usageSource, /const siteLabel = maintenance\?\.site\?\.name/)
      assert.match(
        usageSource,
        /\$\{maintenance\?\.address\?\.fullAddress \?\? maintenance\?\.addressText \?\? ''\}）/
      )
      assert.match(
        usageSource,
        /maintenance\?\.address\?\.fullAddress \?\? maintenance\?\.addressText/
      )
      assert.match(usageSource, /const chainNames = \[/)
      assert.match(usageSource, /maintenance\?\.siteLocation\?\.name,/)
      assert.match(
        usageSource,
        /\.\.\.\(maintenance\?\.extraSiteLocations \?\? \[\]\)\.map\(\(item\) => item\?\.name\)/
      )
      assert.match(usageSource, /\[siteLabel, \.\.\.chainNames\]\.filter\(Boolean\)\.join\(' \/ '\)/)
    }
  })

  it('accepts a referenced customer address without manual division input', () => {
    for (const relativePath of [
      '../../delivery-business/site-survey/index.vue',
      '../../engineering/site-survey/index.vue',
      '../../engineering/installation/index.vue'
    ]) {
      const usageSource = source(relativePath)
      assert.match(
        usageSource,
        /!maintenance\?\.site\?\.id && !maintenance\?\.address\?\.id && !maintenance\.address\?\.detailAddress/
      )
      assert.match(
        usageSource,
        /!maintenance\?\.site\?\.id && !maintenance\?\.address\?\.id && !maintenance\.address\?\.provinceCode/
      )
    }
  })

  it('exposes required department code maintenance', () => {
    const deptSource = source('../../../system/dept/DeptForm.vue')
    assert.match(deptSource, /formData\.code/)
    assert.match(deptSource, /部门编码不能为空/)
  })

  it('shows customer locations, sites and their positions as a tree table', () => {
    const panelSource = source('../../customer/components/CustomerLocationPanel.vue')
    assert.match(panelSource, /:tree-props="\{ children: 'children' \}"/)
    assert.match(panelSource, /default-expand-all/)
    assert.match(panelSource, /customerId: props\.customer\.id/)
    assert.match(panelSource, /getSiteLocationTree/)
    assert.match(panelSource, /关联站点信息需要资产地点查询权限/)
  })

  it('hides auto-generated codes from location input forms', () => {
    assert.doesNotMatch(source('./site/index.vue'), /保存时根据客户自动生成/)
    assert.doesNotMatch(source('./site/LocationTreeDrawer.vue'), /保存时根据站点自动生成/)
  })

  it('selects site and location types from data dictionaries', () => {
    const siteSource = source('./site/index.vue')
    assert.match(siteSource, /getStrDictOptions\(DICT_TYPE\.PMS_SITE_TYPE\)/)
    assert.match(siteSource, /<dict-tag :type="DICT_TYPE\.PMS_SITE_TYPE"/)
    assert.doesNotMatch(siteSource, /例如：CUSTOMER_SITE/)
    const drawerSource = source('./site/LocationTreeDrawer.vue')
    assert.match(drawerSource, /getStrDictOptions\(DICT_TYPE\.PMS_SITE_LOCATION_TYPE\)/)
    assert.doesNotMatch(drawerSource, /例如：ROOM/)
  })

  it('widens the customer detail drawer and adds a service level tab', () => {
    const drawerSource = source('../../customer/components/CustomerFormDrawer.vue')
    assert.match(drawerSource, /size="min\(1080px, 92vw\)"/)
    assert.match(drawerSource, /label="服务等级" name="service-level"/)
    assert.match(drawerSource, /<CustomerServiceLevelPanel :customer="customer"/)
    const panelSource = source('../../customer/components/CustomerServiceLevelPanel.vue')
    assert.match(panelSource, /getServiceLevelPage/)
    assert.match(panelSource, /customerId: props\.customer\.id/)
    assert.match(panelSource, /DICT_TYPE\.PMS_SERVICE_LEVEL/)
    assert.match(panelSource, /DICT_TYPE\.PMS_SRV_LEVEL_STATUS/)
  })

  it('renames the contact section to customer contact person', () => {
    const drawerSource = source('../../customer/components/CustomerFormDrawer.vue')
    assert.match(drawerSource, /来源与客户联系人/)
    assert.doesNotMatch(drawerSource, /来源与联系方式/)
    const contactSource = source('../../customer/components/CustomerSourcePanel.vue')
    assert.match(contactSource, /<h3>客户联系人<\/h3>/)
  })
})
