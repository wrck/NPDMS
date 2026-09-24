import { getContractPage, type ContractRespVO } from '@/api/pms/commerce'

export type SelectedContract = ContractRespVO

/** 复用既有合同主档范围查询和现有实体选择器，不创建第二份合同目录。 */
export const getSelectableContracts = async (params: PageParam & { keyword?: string }) => {
  const keyword = params.keyword?.trim()
  const query = { pageNo: params.pageNo, pageSize: params.pageSize }
  const page = keyword ? await getContractPage({ ...query, contractNo: keyword }) : await getContractPage(query)
  return { list: (page.list || []) as ContractRespVO[] }
}
