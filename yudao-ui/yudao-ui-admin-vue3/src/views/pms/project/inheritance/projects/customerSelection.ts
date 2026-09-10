import { getCustomerPage, type CustomerRespVO } from '@/api/pms/customer'

export type SelectedCustomer = CustomerRespVO

/** 复用原客户名称/编码范围查询和现有实体选择器，不创建第二份客户目录。 */
export const getSelectableCustomers = async (params: PageParam & { keyword?: string }) => {
  const keyword = params.keyword?.trim()
  const page = { pageNo: params.pageNo, pageSize: params.pageSize, lifecycleStatus: 'ENABLED' as const }
  const results = keyword
    ? await Promise.all([getCustomerPage({ ...page, code: keyword }), getCustomerPage({ ...page, name: keyword })])
    : [await getCustomerPage(page)]
  const customers = results.flatMap(result => (result.list || []) as CustomerRespVO[])
    .filter(customer => customer.lifecycleStatus === 'ENABLED')
  return { list: [...new Map(customers.map(customer => [customer.code, customer])).values()] }
}
