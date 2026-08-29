import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')

describe('F-COM-001 commerce workbench contracts', () => {
  it('uses only the approved REST surface and concurrency headers', () => {
    const api = read('../../../api/pms/commerce/index.ts')
    for (const path of [
      '/contracts',
      '/sales-orders',
      '/order-lines',
      '/delivery-scopes/actions/preview',
      '/delivery-scopes/actions/assign',
      '/actions/adjust',
      '/actions/release'
    ])
      expect(api).toContain(path)
    expect(api).toContain("'If-Match'")
    expect(api).toContain("'Idempotency-Key'")
    expect(api).not.toMatch(/pm_order_data_from_erp|pm_order_line_from_erp|pm_project_product_line/)
  })

  it('requires complete project context and exposes server preview facts', () => {
    const page = read('./delivery-scope/index.vue')
    const editor = read('./delivery-scope/DeliveryScopeEditor.vue')
    expect(page).toContain('parseProjectRouteContext')
    expect(page).toContain('projectVersion')
    expect(page).toContain('projectScopeVersion')
    expect(editor).toContain('previewDeliveryScope')
    expect(editor).toContain('validationErrors')
    expect(editor).toContain('occupiedScopes')
    expect(editor).toContain('officeDepartmentName')
    expect(editor).not.toMatch(/itemCode\s*\|\||productCode\s*\|\|\s*itemCode/)
  })

  it('keeps authorization at exact server controls and preserves history access', () => {
    const contracts = read('./contracts/index.vue') + read('./contracts/detail.vue')
    const scopes = read('./delivery-scope/index.vue')
    for (const permission of [
      'pms:commerce:contract:relate',
      'pms:commerce:scope:assign',
      'pms:commerce:scope:adjust',
      'pms:commerce:scope:release'
    ])
      expect(contracts + scopes).toContain(permission)
    expect(scopes).toContain('includeHistory')
    expect(scopes).toContain('DeliveryScopeHistoryDrawer')
    expect(contracts + scopes).not.toMatch(/hasRole|roleCode|合同管理员.*===/)
  })

  it('contains responsive narrow-screen layouts without fixed page width', () => {
    for (const source of [read('./contracts/index.vue'), read('./delivery-scope/index.vue')]) {
      expect(source).toContain('@media (max-width: 767px)')
      expect(source).toContain('min-width: 0')
      expect(source).not.toMatch(/min-width:\s*[1-9]\d{2,}px/)
    }
  })
})
