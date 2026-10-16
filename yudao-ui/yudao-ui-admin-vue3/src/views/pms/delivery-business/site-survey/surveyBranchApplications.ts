import { getOutsourceRequest } from '@/api/pms/engineering/outsource'
import { getMaterialExchangePage, getMaterialExchange } from '@/api/pms/engineering/material-exch'
import { getMaterialRequisitionPage, getMaterialRequisition } from '@/api/pms/engineering/material-req'
import { getExternalProcurementPage, getExternalProcurement } from '@/api/pms/engineering/ext-proc'

export const branchApplications = {
  exchange: { title: 'CRM 改单（独立流程）', button: '新增换货申请', permission: 'pms:imp-material-exch', page: getMaterialExchangePage, get: getMaterialExchange },
  material: { title: '物料申领（独立流程）', button: '发起领料申请', permission: 'pms:imp-material-req', page: getMaterialRequisitionPage, get: getMaterialRequisition },
  procurement: { title: '外采事前申请（独立流程）', button: '发起外采申请', permission: 'pms:imp-ext-proc', page: getExternalProcurementPage, get: getExternalProcurement },
  outsource: { title: '整单转包（独立流程）', button: '发起转包申请', permission: 'pms:res-outsource', get: getOutsourceRequest }
}
export type BranchKind = keyof typeof branchApplications
export const loadBranchApplications = async (kind: Exclude<BranchKind, 'outsource'>, projectId: number, surveyId: number | string,
  readPage = branchApplications[kind].page) => {
  const records: any[] = []
  let pageNo = 1
  while (true) {
    const query = { projectId, pageNo, pageSize: 100, ...(kind === 'exchange' ? {} : { triggerSource: 'SITE_SURVEY' }) }
    const page = await readPage(query)
    const list = page.list || []
    records.push(...list.filter((row: any) => String(row.projectId) === String(projectId) &&
      (kind === 'exchange' ? row.remark === `来源工勘 #${surveyId}` :
        row.triggerSource === 'SITE_SURVEY' && String(row.triggerRefId) === String(surveyId))))
    if (!list.length || pageNo * query.pageSize >= Number(page.total)) break
    pageNo++
  }
  return records
}
