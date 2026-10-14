import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, type TestNode } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
import CompletionPrint from './CompletionCertificatePrintV2.vue'
import SatisfactionPrint from './SatisfactionPrintV2.vue'
import { printValues } from './confirmationPrintForm'
import { wordPrintDate } from './wordPrintFormat'
import type { CompletionConfirmationDetail } from '@/api/pms/acceptance/completion-certificate'
import type { QuestionnaireDefinition } from '@/api/pms/acceptance/satisfaction'

vi.mock('@form-create/element-ui', () => ({ default: { component: vi.fn() } }))
vi.mock('@form-create/designer', () => ({ default: { formCreate: { component: vi.fn() } } }))
const mounted: Array<{ unmount: () => void }> = []
const render = (component: any, modelValue?: any) => {
  const result = mount(component, { modelValue })
  mounted.push(result.app)
  return result.root
}
const nodes = (node: TestNode): TestNode[] => [node, ...node.children.flatMap(nodes)]
const text = (node: TestNode): string => (node.text || '') + node.children.map(text).join('')
const hasClass = (node: TestNode, value: string) => String(node.props?.class || '').split(' ').includes(value)
const withClass = (root: TestNode, value: string) => nodes(root).filter(node => hasClass(node, value))
const signature = (root: TestNode, label: string) => nodes(root).find(node => node.type === 'img' && node.props?.alt === label)
afterEach(() => mounted.splice(0).forEach(app => app.unmount()))
const questionnaire: QuestionnaireDefinition = {
  schemaVersion: 1,
  questions: [
    { code: 'quality', title: '总体工程质量', type: 'RATING', required: true, options: [
      { code: 'EXCELLENT', label: '很满意', score: 100 }, { code: 'GOOD', label: '满意', score: 80 }, { code: 'BAD', label: '不满意', score: 40 }
    ] },
    { code: 'repurchase', title: '是否有意愿再次购买我司设备', type: 'TEXT', required: false },
    { code: 'productAdvice', title: '对产品质量的意见', type: 'TEXT', required: false },
    { code: 'serviceAdvice', title: '对服务质量的意见', type: 'TEXT', required: false }
  ]
}
const detail = (): CompletionConfirmationDetail => ({
  certificate: { id: 1, version: 4, projectId: 10, code: 'INTERNAL-CODE', name: '冻结项目名称', customerUnit: '客户单位',
    contractNo: '合同-001', serviceType: '工程督导', engineerName: '服务工程师', engineerContact: '工程师联系电话', completionDate: '2026-09-30',
    itemArrival: '是', itemInstall: '否', itemCutover: '不涉及', itemTraining: '是', itemDocs: '是',
    devices: [{ deviceType: '防火墙', deviceModel: 'FW-01', quantity: 2 }, { deviceType: '交换机', deviceModel: 'SW-02', quantity: 3 }],
    customerSignUrl: '/obsolete-draft-signature.png', customerSignDate: '2026-09-29', vendorSignUrl: '/vendor-signature.png', vendorSignDate: '2026-10-01' },
  survey: { taskId: 11, collectionKey: 'SURVEY-INTERNAL-CODE', revisionNo: 1, status: 'COMPLETED',
    questionnaireJson: JSON.stringify(questionnaire), answerSnapshot: JSON.stringify({ answers: [
      { questionCode: 'quality', value: 'GOOD' }, { questionCode: 'repurchase', value: '其他：下一期再采购' },
      { questionCode: 'productAdvice', value: '第一行意见\n第二行意见' }, { questionCode: 'serviceAdvice', value: '服务及时' }
    ] }), submittedAt: '2026-10-10 12:30:00', signatureImageDataUrl: 'data:image/png;base64,canonical' },
  history: []
})

describe('Word V2.0 completion print data', () => {
  it('prints saved device quantities and each service choice without exposing the internal code', () => {
    const host = render(CompletionPrint, printValues(detail()).completionPrintData)
    const rows = withClass(host, 'word-device-row').slice(0, 2)
    expect(rows.map(row => row.children.filter(child => child.type === 'td').map(text))).toEqual([
      ['防火墙', '2'], ['交换机', '3']
    ])
    const checked = withClass(host, 'service-choices').map(cell => withClass(cell, 'checked').map(box => text(box.parent!)))
    expect(checked).toEqual([['√是'], ['√否'], ['√不涉及'], ['√是'], ['√是']])
    expect(text(withClass(withClass(host, 'service-type')[0], 'checked')[0].parent!)).toBe('工程督导√')
    expect(text(host)).toContain('2026年 9月 30日')
    expect(text(host)).not.toContain('INTERNAL-CODE')
  })

  it('prints the canonical joint signature on both forms and keeps the independent vendor signature', () => {
    const saved = detail(), before = JSON.stringify(saved), values = printValues(saved)
    for (const [component, data] of [[CompletionPrint, values.completionPrintData], [SatisfactionPrint, values.satisfactionPrintData]] as const) {
      const host = render(component, data)
      expect(signature(host, '甲方签字')?.props?.src).toBe(saved.survey!.signatureImageDataUrl)
      expect(signature(host, '服务方签字')?.props?.src).toBe('/vendor-signature.png')
      expect(text(host)).toContain('2026年 10月 10日')
      expect(text(host)).toContain('2026年 10月 1日')
    }
    expect(JSON.stringify(saved)).toBe(before)
  })

  it('uses the frozen option codes and full multiline opinions instead of the blank reference questions', () => {
    const host = render(SatisfactionPrint, printValues(detail()).satisfactionPrintData)
    expect(withClass(host, 'rating-row')).toHaveLength(1)
    expect(withClass(host, 'rating-row')[0].children.filter(child => child.type === 'td').map(text)).toEqual(['', '√', '', ''])
    expect(text(host)).toContain('下一期再采购')
    expect(text(host)).toContain('第一行意见\n第二行意见')
    expect(text(host)).toContain('服务及时')
    expect(text(withClass(host, 'customer-date')[0])).not.toContain('工程师联系电话')
    expect(text(host)).not.toContain('SURVEY-INTERNAL-CODE')
  })

  it('leaves unsigned dates blank and renders the complete reference matrix for an empty template preview', () => {
    const host = render(SatisfactionPrint)
    expect(withClass(host, 'rating-row')).toHaveLength(10)
    expect(withClass(host, 'category-cell').map(cell => cell.props?.rowspan)).toEqual([3, 2, 5])
    expect(signature(host, '甲方签字')).toBeUndefined()
    expect(text(withClass(host, 'customer-date')[0])).toContain('____年 ____月 ____日')
    expect(wordPrintDate('2026-09-30T23:59:00+08:00')).toBe('2026年 9月 30日')
    expect(wordPrintDate()).toBe('____年 ____月 ____日')
  })

  it('preserves the V1 field bindings and recovers historical envelope fields only for Word rendering', () => {
    const saved = detail(), values = printValues(saved)
    expect(values.name).toBe(saved.certificate.name)
    expect(values.signatureImageDataUrl).toBe(saved.survey!.signatureImageDataUrl)
    expect(values.serviceItemsText).toContain('完成设备硬件安装和软件调测：否')
    expect(values.devicesText).toBe('防火墙 · FW-01 × 2\n交换机 · SW-02 × 3')
    const legacy: CompletionConfirmationDetail = { certificate: { id: 2, projectId: 10, name: '历史项目', content: JSON.stringify({ customerUnit: '历史客户',
      items: { i1: '不涉及' }, customerSignUrl: '/legacy.png', customerSignDate: '2020-01-02', devices: [{ type: '路由器', model: 'OLD-1', qty: 4 }] }) }, history: [] }
    const before = JSON.stringify(legacy), recovered = printValues(legacy).completionPrintData
    expect(recovered.certificate.customerUnit).toBe('历史客户')
    expect(recovered.certificate.devices?.[0].quantity).toBe(4)
    expect(recovered.signatureImageDataUrl).toBe('/legacy.png')
    expect(JSON.stringify(legacy)).toBe(before)
  })
})
