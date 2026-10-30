import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import { describe, it } from 'node:test'

describe('PmsLocationSelector contract', () => {
  const source = readFileSync(new URL('./index.vue', import.meta.url), 'utf8')

  it('supports existing, new and fallback location modes', () => {
    assert.match(source, /选择已有地点/)
    assert.match(source, /现场维护新地点/)
    assert.match(source, /UNRESOLVED/)
  })

  it('submits a nested location maintenance command', () => {
    assert.match(source, /address:/)
    assert.match(source, /site: \{ siteType: 'CUSTOMER_SITE', customerId: customerId\.value \}/)
    assert.match(source, /siteLocation:/)
    assert.match(source, /fallbackLocation/)
  })

  it('references an existing location without mutable fields', () => {
    assert.match(source, /id: location\.id,\s*expectedVersion: location\.version/)
    assert.doesNotMatch(
      source,
      /id: location\.id,\s*expectedVersion: location\.version,\s*(code|name|locationType|treeSort):/
    )
  })

  it('does not cap the site location tree depth', () => {
    assert.match(source, /位置树不限定层级/)
    assert.doesNotMatch(source, /maxDepth|depthLimit/)
  })

  it('defaults country to CN without an input and shows full site addresses', () => {
    assert.match(source, /countryCode: 'CN'/)
    assert.doesNotMatch(source, /国家编码|国家名称/)
    assert.match(source, /:label="siteLabel\(site\)"/)
    assert.match(source, /getAddressPage/)
    assert.match(source, /site\.addressId\)\?\.fullAddress/)
    assert.doesNotMatch(source, /site\.code \|\| ''/)
  })

  it('places the division cascade before the detail address input', () => {
    const divisionAt = source.indexOf('<PmsDivisionInput')
    const detailAt = source.indexOf('addressDraft.detailAddress')
    assert.ok(divisionAt > -1 && detailAt > divisionAt)
  })

  it('nests the site location tree to every existing level', () => {
    assert.match(source, /handleTree\(\s*await LocationApi\.getSiteLocationTree/)
    assert.match(source, /default-expand-all/)
  })

  it('displays the selected site as name with full address', () => {
    assert.match(source, /draft\.fallbackLocation = site \? siteLabel\(site\) : undefined/)
    assert.match(source, /\[site \? siteLabel\(site\) : '', location\?\.name\]/)
  })

  it('links new sites to the project customer and offers customer addresses', () => {
    assert.match(source, /ProjectApi\.getProject/)
    assert.match(source, /CustomerApi\.getCustomerByCode\(project\.customerCode\)/)
    assert.match(source, /locationType === 'ADDRESS'/)
    assert.match(source, /关联客户：\{\{ customerName \}\}/)
    assert.match(source, /draft\.site\.customerId = customerId\.value/)
  })

  it('keeps a selected customer address as a reference-only address input', () => {
    assert.match(source, /draft\.address = \{ id: address\.id, expectedVersion: address\.version \}/)
    assert.match(source, /draft\.addressText = address\.fullAddress/)
    assert.doesNotMatch(source, /draft\.address = \{[^}]*fullAddress/)
    assert.match(source, /selectedCustomerAddressId\.value = undefined/)
  })

  it('previews the full site and location path while creating', () => {
    assert.match(source, /保存路径：\{\{ chainPreview \}\}/)
    assert.match(source, /siteDraft\.value\.name \|\| '（站点名称待填）'/)
    assert.match(source, /siteLocationDraft\.value\.name/)
    assert.match(source, /保存后位置自动追加为站点的下一级/)
  })

  it('hides auto-generated codes from the input form', () => {
    assert.doesNotMatch(source, /保存时自动生成/)
  })

  it('selects location types from the shared dictionary in both modes', () => {
    const matches = source.match(/getStrDictOptions\(DICT_TYPE\.PMS_SITE_LOCATION_TYPE\)/g) || []
    assert.ok(matches.length >= 2)
    assert.doesNotMatch(source, /楼栋\/楼层\/机房\/机柜|例如：ROOM/)
  })

  it('appends chained child locations through one shared block for both modes', () => {
    assert.doesNotMatch(source, /站点内位置（可选）/)
    assert.equal((source.match(/追加下级位置（可选）/g) || []).length, 1)
    assert.match(
      source,
      /mode\.value === 'new' \|\| \(mode\.value === 'existing' && !!selectedSiteId\.value\)/
    )
    assert.match(
      source,
      /draft\.extraSiteLocations\.push\(\{ name, locationType: extraDraft\.locationType, treeSort: 0 \}\)/
    )
    assert.match(source, /extraSiteLocations: \[\]/)
    assert.match(source, /draft\.extraSiteLocations = \[\]/)
  })

  it('starts the location chain with the first appended location in new mode', () => {
    assert.match(source, /if \(mode\.value === 'new' && !draft\.siteLocation\?\.name\)/)
    assert.match(
      source,
      /draft\.siteLocation = \{ code: '', name, locationType: extraDraft\.locationType, treeSort: 0 \}/
    )
    assert.match(source, /const chainItems = computed<ChainItem\[\]>/)
    assert.match(source, /const removeChainItem = \(index: number\)/)
    assert.match(source, /draft\.extraSiteLocations\?\.shift\(\)/)
    assert.doesNotMatch(source, /showNextLevelRow/)
  })

  it('reflects the full location chain in preview and saved text', () => {
    assert.match(source, /const findLocationPath = \(/)
    assert.match(source, /const next = \[\.\.\.trail, node\]/)
    assert.match(source, /\.\.\.path\.map\(\(node\) => node\.name\),/)
    assert.match(source, /if \(mode\.value === 'existing'\) draft\.fallbackLocation = existingPathPreview\.value \|\| undefined/)
    assert.doesNotMatch(source, /watch\(existingPathPreview/)
  })
})
