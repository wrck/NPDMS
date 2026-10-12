import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import ts from 'typescript'
import { describe, expect, it, vi } from 'vitest'

// Execute the page's actual loader without mounting unrelated project panels.
const source = readFileSync(resolve(process.cwd(), 'src/views/pms/project/project-master-detail/index.vue'), 'utf8')
const equipmentSource = source.slice(
  source.indexOf('// ============ 交付模块表格'),
  source.indexOf('// 序列号详情行 → 配置Log')
)
const loaderCode = ts.transpileModule(equipmentSource, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None }
}).outputText

const paged = (rows: any[]) => vi.fn(async ({ pageNo, pageSize }) => ({
  list: rows.slice((pageNo - 1) * pageSize, pageNo * pageSize), total: rows.length
}))
const loader = (scopes: any[], devices: any[], lines: any[]) => {
  const scopePage = paged(scopes)
  const devicePage = paged(devices)
  const linePage = vi.fn(async (params) => paged(lines.filter(l => l.orderNo === params.orderNo))(params))
  const load = new Function('getDeliveryScopePage', 'DeviceApi', 'getSalesOrderLinePage', 'switchTab',
    `${loaderCode}\nreturn deliveryModules.equipment.load`)(scopePage, { getDevicePage: devicePage }, linePage, vi.fn())
  return { load, scopePage, devicePage, linePage }
}

describe('equipment list pagination with original quantity aggregation', () => {
  it('counts all 699 devices instead of the first 200', async () => {
    const devices = Array.from({ length: 699 }, (_, i) => ({ sn: `SN${i}`, productCode: ' P1 ', productName: '设备' }))
    const ctx = loader([{ orderNo: 'SO1', lineNo: '1', details: [{ productCode: 'P1', allocatedQuantity: 699 }] }],
      devices, [{ orderNo: 'SO1', lineNo: '1', deliveredQty: 699, openQty: 0 }])
    const result = await ctx.load(123, 1, 20)
    expect(result).toMatchObject({ total: 1, list: [{ projectQty: 699, deviceCount: 699, deliveredQty: 699 }] })
    expect(ctx.devicePage.mock.calls.map(([q]) => q.pageNo)).toEqual([1, 2, 3, 4])
  })

  it('loads later scope and order pages, deduplicating quantities by order and line', async () => {
    const scopes = Array.from({ length: 201 }, (_, i) => ({
      orderNo: 'SO1', lineNo: String(i + 1), details: [{ productCode: 'P1', allocatedQuantity: 1 }]
    }))
    scopes.push({ orderNo: 'SO2', lineNo: '1', details: [{ productCode: 'P1', allocatedQuantity: 2 }] })
    scopes.push({ orderNo: 'SO2', lineNo: '1', details: [{ productCode: 'P1', allocatedQuantity: 3 }] })
    const lines = Array.from({ length: 201 }, (_, i) => ({ orderNo: 'SO1', lineNo: String(i + 1), deliveredQty: 1, openQty: 0 }))
    lines.push({ orderNo: 'SO2', lineNo: '1', deliveredQty: 4, openQty: 1 })
    const ctx = loader(scopes, [], lines)
    expect(await ctx.load(123, 1, 20)).toMatchObject({
      total: 1, list: [{ orderNo: 'SO1、SO2', projectQty: 206, deliveredQty: 205, openQty: 1, deviceCount: 0 }]
    })
  })

  it('shows only scope products, preserving other devices outside the product list', async () => {
    const scopes = [
      { orderNo: 'SO1', lineNo: '1', details: [{ productCode: 'PARENT', allocatedQuantity: 4 }] },
      { orderNo: 'SO1', lineNo: '2', details: [{ productCode: 'OPTIC', allocatedQuantity: 16 }] }
    ]
    const devices = ['PARENT', 'OPTIC', 'COMPONENT'].flatMap((productCode, n) =>
      Array.from({ length: [4, 16, 8][n] }, (_, i) => ({ productCode, sn: `${productCode}${i}` })))
    const ctx = loader(scopes, devices, [
      { orderNo: 'SO1', lineNo: '1', deliveredQty: 4, openQty: 0 },
      { orderNo: 'SO1', lineNo: '2', deliveredQty: 16, openQty: 0 }
    ])
    const result = await ctx.load(123, 1, 20)
    expect(result.total).toBe(2)
    expect(result.list.map(r => r.productCode)).toEqual(['PARENT', 'OPTIC'])
    expect(result.list[0]).toMatchObject({ orderNo: 'SO1', projectQty: 4, deliveredQty: 4, deviceCount: 4 })
    expect(result.list[1]).toMatchObject({ orderNo: 'SO1', projectQty: 16, deliveredQty: 16, deviceCount: 16 })
    expect(devices).toHaveLength(28)
  })
})
