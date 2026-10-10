import type { JsonObject } from '@/api/pms/platform/dynamic-form'
import type { CompletionConfirmationDetail } from '@/api/pms/acceptance/completion-certificate'
import type { CompletionCertificateVO } from '@/api/pms/acceptance/completion-certificate'
import type { QuestionnaireDefinition } from '@/api/pms/acceptance/satisfaction'
import formCreate from '@form-create/element-ui'
import FcDesigner from '@form-create/designer'
import QuestionnairePrintField from './QuestionnairePrintField.vue'
import CompletionCertificatePrintV2 from './CompletionCertificatePrintV2.vue'
import SatisfactionPrintV2 from './SatisfactionPrintV2.vue'
import { formatDate } from '@/utils/formatTime'
export const registerConfirmationPrintComponents = () => {
  for (const renderer of [formCreate, FcDesigner.formCreate]) {
    renderer.component('CompletionSatisfactionQuestionnaire', QuestionnairePrintField)
    renderer.component('CompletionCertificatePrintV2', CompletionCertificatePrintV2)
    renderer.component('CompletionSatisfactionPrintV2', SatisfactionPrintV2)
  }
}
export type ConfirmationDocumentKind = 'COMPLETION' | 'SATISFACTION'
export interface ConfirmationWordPrintData {
  certificate: CompletionCertificateVO
  signatureImageDataUrl?: string
  customerSignDate?: string
  definition: QuestionnaireDefinition
  answers: Record<string, string | string[]>
}
export const defaultPrintTemplateCode = (kind: ConfirmationDocumentKind) => `${kind}_PRINT_WORD_V2`
export const printCategory = (kind: ConfirmationDocumentKind) => `${kind}_PRINT`
export const documentLabel = (kind: ConfirmationDocumentKind) => kind === 'COMPLETION' ? '完工证明' : '满意度调查表'
// Preserve the original read-only display of pre-V386 records whose fields were stored in an envelope.
export const completionDisplay = (record: CompletionCertificateVO): CompletionCertificateVO => {
  if (record.serviceType || record.engineerName || record.engineerContact || record.customerUnit || record.contractNo
      || record.devices?.length || record.customerSignUrl || record.vendorSignUrl
      || [record.itemArrival, record.itemInstall, record.itemCutover, record.itemTraining, record.itemDocs].some(Boolean)) return record
  const parse = (raw?: string) => { try { const value = JSON.parse(raw || 'null'); return value && typeof value === 'object' && !Array.isArray(value) ? value : null } catch { return null } }
  const old = parse(record.content) ?? parse(record.remark)
  if (!old) return record
  return { ...record, serviceType: old.serviceType, engineerName: old.engineer, engineerUserId: old.engineerUserId,
    engineerContact: old.contact, customerUnit: old.customerUnit, contractNo: old.contractNo,
    itemArrival: old.items?.i1, itemInstall: old.items?.i2, itemCutover: old.items?.i3, itemTraining: old.items?.i4, itemDocs: old.items?.i5,
    customerSignUrl: old.customerSignUrl, customerSignDate: old.customerSignDate, vendorSignUrl: old.vendorSignUrl, vendorSignDate: old.vendorSignDate,
    devices: old.devices?.map((item: any) => ({ deviceType: String(item?.type || ''), deviceModel: String(item?.model || ''), quantity: Number(item?.qty || 1) || 1 })) || [] }
}
export const createConfirmationPrintForm = (kind: ConfirmationDocumentKind) => {
  return {
    formConfJson: { form: { labelPosition: 'top', labelWidth: 'auto' }, submitBtn: false, resetBtn: false },
    formRulesJson: [{
      type: kind === 'COMPLETION' ? 'CompletionCertificatePrintV2' : 'CompletionSatisfactionPrintV2',
      field: kind === 'COMPLETION' ? 'completionPrintData' : 'satisfactionPrintData'
    }, ...['name', 'signatureImageDataUrl', kind === 'COMPLETION' ? 'completionDate' : 'answersText']
      .map(field => ({ type: 'input', field, hidden: true }))] as JsonObject[]
  }
}
export const surveyAnswers = (detail?: CompletionConfirmationDetail) => {
  const answers: Record<string, string | string[]> = {}
  for (const item of JSON.parse(detail?.survey?.answerSnapshot || '{"answers":[]}').answers) answers[item.questionCode] = item.value
  return answers
}
export const printValues = (detail: CompletionConfirmationDetail) => {
  const certificate = detail.certificate
  const definition: QuestionnaireDefinition = JSON.parse(detail.survey?.questionnaireJson || '{"questions":[]}')
  const answers = surveyAnswers(detail)
  const wordCertificate = completionDisplay(certificate)
  const wordData: ConfirmationWordPrintData = {
    certificate: wordCertificate, definition, answers,
    signatureImageDataUrl: detail.survey?.signatureImageDataUrl || wordCertificate.customerSignUrl,
    customerSignDate: detail.survey?.submittedAt ? formatDate(detail.survey.submittedAt, 'YYYY-MM-DD') : wordCertificate.customerSignDate
  }
  return { ...certificate, ...detail.survey, questionnaireData: { definition, answers },
    completionPrintData: wordData, satisfactionPrintData: wordData,
    signTime: detail.survey?.submittedAt ? formatDate(detail.survey.submittedAt) : certificate.customerSignDate || '',
    serviceItemsText: [['完成到货验收', certificate.itemArrival], ['完成设备硬件安装和软件调测', certificate.itemInstall], ['完成业务上线/割接', certificate.itemCutover], ['完成现场讲解和培训', certificate.itemTraining], ['工程文档已移交', certificate.itemDocs]].map(([label, value]) => `${label}：${value || '—'}`).join('\n'),
    devicesText: certificate.devices?.map(item => `${item.deviceType || '—'} · ${item.deviceModel || '—'} × ${item.quantity}`).join('\n') || '',
    answersText: definition.questions.map(question => {
      const values = Array.isArray(answers[question.code]) ? answers[question.code] as string[] : [answers[question.code]]
      return `${question.title}：${values.map(value => question.options?.find(option => option.code === value)?.label || value || '未填写').join('、')}`
    }).join('\n')
  }
}
