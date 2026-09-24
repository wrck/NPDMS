import request from '@/config/axios'

/** 产品信息（CRM 只读副本，由集成同步写入）：换货产品下拉数据源 */
export interface ProductOfficialVO {
  id?: number
  productCode?: string
  productName?: string
  productModel?: string
  /** 发布状态：ACTIVE 已发布 / FAST001_TEST_PUBLISHED 测试发布（下拉禁选） */
  status?: string
}

export const AssetProductOfficialApi = {
  /** 产品信息分页：关键字跨名称/编码/型号模糊 */
  page: (params: { pageNo?: number; pageSize?: number; keyword?: string }) =>
    request.get({ url: '/api/v1/pms/asset-product-officials/page', params }) as Promise<{
      total: number
      list: ProductOfficialVO[]
    }>
}
