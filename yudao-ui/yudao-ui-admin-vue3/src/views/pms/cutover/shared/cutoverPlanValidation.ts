import type { WritableCutoverPlanContent } from '@/api/pms/cutover'
import type { CutoverPlanDraftContent } from './cutoverPlanDraft'

const stepSections = {
  PRE_OPERATION: '割接前操作清单', OPERATION: '割接执行操作清单', CLOSING_COLLECTION: '割接收尾收集清单',
  POST_BUSINESS_TEST: '割接后业务测试表', ROLLBACK: '回退方案操作步骤', POST_CUTOVER_SUPPORT: '割接后保障'
} as const
const validTime = (value: unknown) => /^(0|[1-9][0-9]*)$/.test(String(value))
  && Number.isSafeInteger(Number(value)) && Number(value) > 0

// Match existing per-row save requirements; empty collections and the optional draft plan time remain valid.
export const planContentValidation = (content: CutoverPlanDraftContent) => {
  if (content.scheduledTime != null && !validTime(content.scheduledTime))
    return { message: '计划割接时间格式无效，请重新选择日期和时间。', fieldTestId: 'plan-scheduled-time' }
  if (content.editMode === 'FULL_FILE_UPLOAD') return null
  if (content.editMode === 'ONLINE_TEMPLATE_STANDARD') {
    for (const [index, row] of content.overview.scheduleTable.entries()) {
      if (!validTime(row.plannedAt)) return {
        message: `项目割接计划第${index + 1}行的日期未填写或格式无效，请重新选择日期和时间。`,
        fieldTestId: `plan-schedule-${index}-plannedAt`
      }
      if (!row.content.trim() || row.content.trim().length > 1000) return {
        message: `请填写项目割接计划第${index + 1}行的工作内容（1～1000字），或删除该计划项。`,
        fieldTestId: `plan-schedule-${index}-content`
      }
    }
  }
  for (const row of content.steps) {
    if (!row.content.trim() || row.content.trim().length > 4000) {
      const field = content.editMode === 'ONLINE_TEMPLATE_STANDARD' && row.sectionCode === 'POST_CUTOVER_SUPPORT' ? '时间' : '操作内容'
      return { message: `请填写${stepSections[row.sectionCode]}第${row.stepNo}行的${field}（1～4000字），或删除该步骤。`,
        fieldTestId: `plan-step-${row.sectionCode}-${row.stepNo}${content.editMode === 'ONLINE_TEMPLATE_STANDARD' ? '-content' : ''}` }
    }
  }
  return null
}

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
  let card = root.closest('details')
  while (card) {
    card.open = true
    card = card.parentElement?.closest('details') || null
  }
  root.scrollIntoView({ block: 'start' })
  root.focus({ preventScroll: true })
}
