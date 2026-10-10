<template>
  <WordPrintFrameV2 title="客户满意度调查表">
    <p class="greeting">尊敬的客户：</p>
    <p class="introduction">非常感谢您对本次工程实施的大力支持！为进一步提升我们的服务水平及提供更优质量的服务，请您对本次工程实施情况给予评价，谢谢。</p>
    <table class="word-print-table satisfaction-word-table">
      <colgroup><col style="width: 18.9%" /><col style="width: 21%" /><col v-for="(column, index) in columns" :key="column.key" :style="columns.length === 5 ? { width: `${[8.58, 8.58, 7.76, 10.36, 10.82][index]}%` } : undefined" /><col style="width: 14%" /></colgroup>
      <tbody>
        <tr class="metadata-row"><th scope="row">项目名称</th><td :colspan="columns.length + 2">{{ modelValue?.certificate.name }}</td></tr>
        <tr class="evaluation-row"><th :colspan="columns.length + 3">评　价</th></tr>
        <tr class="rating-header"><th scope="col">调查项目</th><th scope="col">调查内容</th><th v-for="column in columns" :key="column.key" scope="col">{{ column.label }}<br />{{ column.score }}</th><th scope="col">备注</th></tr>
        <tr v-for="row in ratingRows" :key="row.question.code" class="rating-row">
          <th v-if="row.span" scope="rowgroup" :rowspan="row.span" class="category-cell">{{ row.category }}</th>
          <th scope="row">{{ row.question.title }}</th>
          <td v-for="column in columns" :key="column.key" :aria-label="`${row.question.title}：${column.label}${column.score}`">{{ selected(row.question, column.key) ? '√' : '' }}</td>
          <td></td>
        </tr>
        <tr v-for="question in otherQuestions" :key="question.code" :class="{ 'opinion-row': question.type === 'TEXT' && question.code !== 'repurchase' }">
          <th scope="row">{{ questionLabel(question) }}</th>
          <td :colspan="columns.length + 2" class="answer-cell">
            <div v-if="question.code === 'repurchase'" class="repurchase">
              <span v-for="choice in ['是', '否']" :key="choice">{{ choice }}<span class="word-check"><span v-if="answer(question.code) === choice">√</span></span></span>
              <span>其它 <span class="other-answer">{{ answer(question.code).startsWith('其他：') ? answer(question.code).slice(3) : answer(question.code) && !['是', '否'].includes(answer(question.code)) ? answer(question.code) : '____________' }}</span></span>
            </div>
            <template v-else>{{ answerText(question) }}</template>
          </td>
        </tr>
        <tr>
          <td :colspan="columns.length + 3" class="customer-signature">
            <div class="customer-signature-layout">
              <div class="signature-content"><span>甲方签章：</span><img v-if="modelValue?.signatureImageDataUrl" :src="modelValue.signatureImageDataUrl" alt="甲方签字" /></div>
              <div class="customer-date"><p>联系方式：</p><p>{{ wordPrintDate(modelValue?.customerSignDate) }}</p></div>
            </div>
          </td>
        </tr>
        <tr>
          <td :colspan="columns.length + 3" class="vendor-signature">
            <div class="vendor-signature-layout"><span>服务方签章：</span><img v-if="modelValue?.certificate.vendorSignUrl" :src="modelValue.certificate.vendorSignUrl" alt="服务方签字" /><span class="vendor-date">{{ wordPrintDate(modelValue?.certificate.vendorSignDate) }}</span></div>
          </td>
        </tr>
      </tbody>
    </table>
    <p class="word-note">注：作为集中调查，必须请客户亲自填写，并传真、邮寄、快递或由调查员带回我公司。</p>
  </WordPrintFrameV2>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import WordPrintFrameV2 from './WordPrintFrameV2.vue'
import { wordPrintDate } from './wordPrintFormat'
import type { ConfirmationWordPrintData } from './confirmationPrintForm'
import type { QuestionnaireQuestion, QuestionnaireOption } from '@/api/pms/acceptance/satisfaction'
const props = defineProps<{ modelValue?: ConfirmationWordPrintData }>()
// Blank template preview only. Submitted records always render their own frozen questions and options.
const referenceGroups = [
  { category: '工程质量', titles: ['总体工程质量', '设备安装规范', '工程交付及时性'] },
  { category: '产品质量', titles: ['功能完善性', '稳定、可靠性'] },
  { category: '服务质量', titles: ['实施人员服务意识及配合度', '问题响应及时性', '实施人员技术水平', '工程培训讲解效果', '工程文档完整性'] }
]
const referenceOptions = [
  { code: '100', label: '很满意', score: 100 }, { code: '80', label: '满意', score: 80 },
  { code: '60', label: '一般', score: 60 }, { code: '40', label: '不满意', score: 40 }, { code: '0', label: '很不满意', score: 0 }
]
const referenceQuestions: QuestionnaireQuestion[] = [
  ...referenceGroups.flatMap(group => group.titles.map(title => ({ code: title, title, type: 'RATING' as const, required: false, options: referenceOptions }))),
  { code: 'repurchase', title: '是否有意愿再次购买我司设备', type: 'TEXT', required: false },
  { code: 'productAdvice', title: '对本公司提供产品质量的意见', type: 'TEXT', required: false },
  { code: 'serviceAdvice', title: '对本公司提供服务质量的意见', type: 'TEXT', required: false }
]
const questions = computed(() => props.modelValue?.definition.questions.length ? props.modelValue.definition.questions : referenceQuestions)
const optionKey = (option: QuestionnaireOption) => `${option.label}\u0000${option.score === undefined ? '' : Number(option.score)}`
const columns = computed(() => Array.from(new Map(questions.value.filter(q => q.type === 'RATING').flatMap(q => q.options ?? [])
  .map(option => [optionKey(option), { key: optionKey(option), label: option.label, score: option.score === undefined ? '' : Number(option.score) }])).values()))
const ratingRows = computed(() => {
  const rows = questions.value.filter(q => q.type === 'RATING').map(question => ({ question,
    category: referenceGroups.find(group => group.titles.includes(question.title))?.category ?? '—', span: 0 }))
  for (let index = 0; index < rows.length;) {
    let end = index + 1
    while (end < rows.length && rows[end].category === rows[index].category) end++
    rows[index].span = end - index
    index = end
  }
  return rows
})
const otherQuestions = computed(() => questions.value.filter(q => q.type !== 'RATING'))
const answer = (code: string) => typeof props.modelValue?.answers[code] === 'string' ? props.modelValue.answers[code] as string : ''
const selected = (question: QuestionnaireQuestion, key: string) => {
  const option = question.options?.find(item => optionKey(item) === key)
  return !!option && props.modelValue?.answers[question.code] === option.code
}
const opinionLabels: Record<string, string> = { '对产品质量的意见': '对本公司提供产品质量的意见', '对服务质量的意见': '对本公司提供服务质量的意见' }
const questionLabel = (question: QuestionnaireQuestion) => opinionLabels[question.title] || question.title
const answerText = (question: QuestionnaireQuestion) => {
  const value = props.modelValue?.answers[question.code]
  return (Array.isArray(value) ? value : [value]).map(code => question.options?.find(option => option.code === code)?.label || code || '').join('、')
}
</script>

<style scoped>
.greeting { margin: 8px 0 16px; font-size: 18.7px; font-weight: 700; line-height: 28px; }
.introduction { margin: 0 0 12px; text-indent: 2em; font-size: 18.7px; line-height: 32px; }
.satisfaction-word-table { width: 169mm; max-width: calc(100% + 4mm); margin-left: -2mm; border: 1px solid #000; border-collapse: collapse; table-layout: fixed; font-size: 14px; line-height: 20px; }
.word-print-table th, .word-print-table td { padding: 0 5px; border: 1px solid #000; vertical-align: middle; text-align: center; overflow-wrap: anywhere; white-space: pre-wrap; }
.word-print-table tr { break-inside: avoid; }
.word-print-table th { font-weight: 400; }
.word-print-table .metadata-row th, .word-print-table .evaluation-row th, .word-print-table .rating-header th { font-weight: 700; }
.metadata-row, .evaluation-row { height: 42px; }
.metadata-row td { text-align: left; }
.rating-header { height: 44px; font-size: 14px; }
.rating-header th { padding: 0 3px; white-space: nowrap; }
.rating-row { height: 20px; line-height: 20px; }
.rating-row td { font-family: Arial, sans-serif; }
.repurchase { min-height: 35px; display: flex; align-items: center; justify-content: space-around; gap: 8px; text-align: left; }
.word-check { display: inline-block; box-sizing: border-box; position: relative; width: 11px; height: 11px; border: 1px solid #000; vertical-align: baseline; }
.word-check span { position: absolute; top: -8px; left: -1px; font-family: Arial, sans-serif; font-size: 16px; }
.other-answer { overflow-wrap: anywhere; }
.opinion-row { height: 84px; }
.word-print-table .answer-cell { text-align: left; }
.word-print-table .customer-signature, .word-print-table .vendor-signature { padding: 5px; text-align: left; }
.customer-signature-layout { min-height: 112px; display: flex; align-items: center; gap: 12px; }
.signature-content { flex: 1; display: flex; align-items: center; min-width: 0; gap: 8px; }
.signature-content > span { flex-shrink: 0; }
.signature-content img { width: 210px; max-width: calc(100% - 80px); height: 90px; object-fit: contain; }
.customer-date { width: 210px; flex-shrink: 0; }
.customer-date p { margin: 12px 0; }
.vendor-signature-layout { min-height: 60px; display: flex; align-items: center; gap: 12px; }
.vendor-signature-layout img { width: 210px; height: 55px; object-fit: contain; }
.vendor-date { margin-left: auto; width: 210px; }
.word-note { margin: 12px 0 0; font-size: 12px; line-height: 18px; }
</style>
