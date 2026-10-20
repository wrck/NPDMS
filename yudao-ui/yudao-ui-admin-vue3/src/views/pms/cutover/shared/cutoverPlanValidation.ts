import type { WritableCutoverPlanContent } from '@/api/pms/cutover'

const supportRoles = {
  CUSTOMER: '客户', DP_FIRST_LINE: '迪普一线工程师', DP_SECOND_LINE: '迪普二线工程师', DP_RND: '迪普研发'
} as const

// Explain an existing server completeness rejection; this does not authorize generation.
export const planSupportValidationMessage = (content: WritableCutoverPlanContent | null) => {
  if (content?.editMode !== 'ONLINE_TEMPLATE_STANDARD') return ''
  const rows = content.supportArrangements
  const missing = Object.entries(supportRoles).filter(([role]) => ['CUSTOMER', 'DP_FIRST_LINE'].includes(role)
    && !rows.some(row => row.roleCode === role))
  if (missing.length) return `保障安排缺少角色：${missing.map(([, label]) => label).join('、')}。请新增人员并补齐保障信息。`
  const index = rows.findIndex(row => !row.personName.trim() || !row.dutyDescription.trim()
    || !row.phone.trim() || !(Number(row.arrivalTime) > 0))
  if (index < 0) return ''
  const row = rows[index]
  const fields = [!row.personName.trim() && '姓名', !row.dutyDescription.trim() && '任务描述',
    !row.phone.trim() && '联系电话', !(Number(row.arrivalTime) > 0) && '到位时间'].filter(Boolean)
  return `保障安排第${index + 1}行（${supportRoles[row.roleCode]}）缺少：${fields.join('、')}。`
}

export const focusPlanSupportValidation = (root: HTMLElement) => {
  root.scrollIntoView({ block: 'start' })
  root.focus({ preventScroll: true })
}
