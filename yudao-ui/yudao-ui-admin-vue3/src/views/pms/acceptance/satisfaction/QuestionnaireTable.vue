<template>
  <div class="survey-sheet">
    <p class="survey-introduction">尊敬的客户：<br />非常感谢您对本次工程实施的大力支持！为进一步提升我们的服务水平，请您对本次工程实施情况给予评价，谢谢。</p>
    <div class="survey-scroll" tabindex="0" aria-label="满意度调查表，可横向滚动">
      <table class="survey-table">
        <caption class="sr-only">满意度调查表</caption>
        <colgroup><col class="category-column" /><col class="question-column" /><col v-for="column in columns" :key="column.key" /><col class="remark-column" /></colgroup>
        <thead><tr><th scope="col">评价类别</th><th scope="col">调查项目</th><th v-for="column in columns" :key="column.key" scope="col">{{ column.label }}</th><th scope="col">备注</th></tr></thead>
        <tbody>
          <tr v-for="row in ratingRows" :key="row.question.code">
            <th v-if="row.span" scope="rowgroup" :rowspan="row.span" class="category-cell">{{ row.category }}</th>
            <th scope="row" class="question-cell">{{ row.question.title }}<span v-if="row.question.required" class="required"> *</span></th>
            <td v-for="column in columns" :key="column.key">
              <label v-if="findOption(row.question, column.key)" class="rating-choice">
                <input type="radio" :name="`${instanceId}-${row.question.code}`" :value="findOption(row.question, column.key)!.code" :checked="answers[row.question.code] === findOption(row.question, column.key)!.code" :disabled="disabled" :aria-label="`${row.question.title}：${column.label}`" @change="answers[row.question.code] = findOption(row.question, column.key)!.code" />
              </label>
              <span v-else aria-label="此题无该选项">—</span>
            </td>
            <td class="remark-cell">—</td>
          </tr>
          <tr v-for="question in otherQuestions" :key="question.code">
            <th colspan="2" scope="row">{{ question.title }}<span v-if="question.required" class="required"> *</span></th>
            <td :colspan="columns.length + 1" class="answer-cell">
              <div v-if="isRepurchase(question)" class="repurchase-answer">
                <el-radio-group :model-value="repurchaseChoice(question.code)" :disabled="disabled" @update:model-value="answers[question.code] = $event === '其他' ? '其他：' : String($event)">
                  <el-radio v-for="choice in ['是', '否', '其他']" :key="choice" :value="choice">{{ choice }}</el-radio>
                </el-radio-group>
                <el-input v-if="repurchaseChoice(question.code) === '其他'" :model-value="single(question.code).slice(3)" :disabled="disabled" :maxlength="(question.maxLength ?? 1000) - 3" aria-label="其他购买意愿" placeholder="请填写" @update:model-value="answers[question.code] = `其他：${$event}`" />
              </div>
              <el-radio-group v-else-if="question.type === 'SINGLE_CHOICE'" :model-value="single(question.code)" :disabled="disabled" @update:model-value="answers[question.code] = String($event)">
                <el-radio v-for="option in question.options" :key="option.code" :value="option.code">{{ option.label }}</el-radio>
              </el-radio-group>
              <el-checkbox-group v-else-if="question.type === 'MULTIPLE_CHOICE'" :model-value="multiple(question.code)" :disabled="disabled" @update:model-value="answers[question.code] = $event.map(String)">
                <el-checkbox v-for="option in question.options" :key="option.code" :value="option.code">{{ option.label }}</el-checkbox>
              </el-checkbox-group>
              <el-input v-else :model-value="single(question.code)" type="textarea" :rows="2" :maxlength="question.maxLength" :disabled="disabled" :aria-label="question.title" show-word-limit @update:model-value="answers[question.code] = $event" />
              <span v-if="question.type === 'MULTIPLE_CHOICE' && question.maxSelections" class="question-help">最多选择 {{ question.maxSelections }} 项</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="showSignature !== false" class="signature-row">
      <section class="signature-box"><p>甲方签章：</p><slot name="customer-signature"></slot></section>
      <section class="signature-box"><p>服务方签章：</p></section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, getCurrentInstance } from 'vue'
import type { QuestionnaireDefinition, QuestionnaireQuestion, QuestionnaireOption } from '@/api/pms/acceptance/satisfaction'
const props = defineProps<{ definition: QuestionnaireDefinition; answers: Record<string, string | string[]>; disabled?: boolean; showSignature?: boolean }>()
const instanceId = getCurrentInstance()!.uid
// Categories come from DemoV2's named items, never from question positions.
const categories: Record<string, string> = {
  '总体工程质量': '工程质量', '设备安装规范': '工程质量', '工程交付及时性': '工程质量',
  '功能完善性': '产品质量', '稳定、可靠性': '产品质量',
  '实施人员服务意识及配合度': '服务质量', '问题响应及时性': '服务质量',
  '实施人员技术水平': '服务质量', '工程培训讲解效果': '服务质量', '工程文档完整性': '服务质量'
}
const scoreLabel = (option: QuestionnaireOption) => option.score === undefined ? '' : String(Number(option.score))
const optionKey = (option: QuestionnaireOption) => `${option.label}\u0000${scoreLabel(option)}`
const columns = computed(() => {
  const options = props.definition.questions.filter(q => q.type === 'RATING').flatMap(q => q.options ?? [])
  return Array.from(new Map(options.map(option => [optionKey(option), { key: optionKey(option), label: `${option.label}${scoreLabel(option)}` }])).values())
})
const ratingRows = computed(() => {
  const rows = props.definition.questions.filter(q => q.type === 'RATING').map(question => ({ question, category: categories[question.title] ?? '—', span: 0 }))
  for (let index = 0; index < rows.length;) {
    let end = index + 1
    while (end < rows.length && rows[end].category === rows[index].category) end++
    rows[index].span = end - index
    index = end
  }
  return rows
})
const otherQuestions = computed(() => props.definition.questions.filter(q => q.type !== 'RATING'))
const findOption = (question: QuestionnaireQuestion, key: string) => question.options?.find(option => optionKey(option) === key)
const single = (code: string) => typeof props.answers[code] === 'string' ? props.answers[code] as string : ''
const isRepurchase = (question: QuestionnaireQuestion) => question.type === 'TEXT' && question.code === 'repurchase' && question.title === '是否有意愿再次购买我司设备'
const repurchaseChoice = (code: string) => single(code).startsWith('其他：') ? '其他' : single(code)
const multiple = (code: string) => Array.isArray(props.answers[code]) ? props.answers[code] as string[] : []
const validate = async () => {
  for (const question of props.definition.questions) {
    const value = props.answers[question.code]
    const length = typeof value === 'string' ? value.trim().length : value?.length ?? 0
    if (question.required && !length) throw new Error(`请填写“${question.title}”`)
    if (!length) continue
    if (question.type === 'TEXT' && (length < (question.minLength ?? 0) || length > (question.maxLength ?? Infinity))) throw new Error(`“${question.title}”的字数不符合要求`)
    if (question.type === 'MULTIPLE_CHOICE' && (length < (question.minSelections ?? 0) || length > (question.maxSelections ?? Infinity))) throw new Error(`“${question.title}”的选择数量不符合要求`)
    if (question.type !== 'TEXT') {
      const values = Array.isArray(value) ? value : [value]
      if (values.some(code => !question.options?.some(option => option.code === code))) throw new Error(`“${question.title}”的选项无效`)
    }
  }
}
defineExpose({ validate })
</script>

<style scoped>
.survey-introduction { margin: 0 0 16px; font-size: 13px; line-height: 1.7; }
.survey-scroll { overflow-x: auto; border: 1px solid var(--el-border-color-lighter); border-radius: 5px; }
.survey-table { width: 100%; min-width: 900px; border-collapse: collapse; table-layout: fixed; font-size: 13px; }
.survey-table th, .survey-table td { height: 46px; box-sizing: border-box; padding: 10px 12px; border-right: 1px solid var(--el-border-color-lighter); border-bottom: 1px solid var(--el-border-color-lighter); text-align: center; overflow-wrap: anywhere; }
.survey-table tr > :last-child { border-right: 0; }
.survey-table tbody tr:last-child > * { border-bottom: 0; }
.survey-table thead th { background: var(--el-fill-color-light); font-weight: 600; }
.survey-table tbody tr:nth-child(even) { background: var(--el-fill-color-lighter); }
.survey-table tbody th { font-weight: 400; }
.category-column { width: 100px; }
.question-column { width: 160px; }
.remark-column { width: 110px; }
.category-cell { background: var(--el-bg-color); }
.required { color: var(--el-color-danger); }
.rating-choice { display: flex; min-height: 26px; align-items: center; justify-content: center; cursor: pointer; }
.rating-choice input { width: 18px; height: 18px; accent-color: var(--el-color-primary); cursor: pointer; }
.survey-table .answer-cell { text-align: left; }
.remark-cell, .question-help { color: var(--el-text-color-secondary); }
.question-help { display: block; font-size: 12px; margin-top: 6px; }
.repurchase-answer { display: flex; align-items: center; justify-content: center; gap: 12px; }
.repurchase-answer :deep(.el-input) { max-width: 280px; }
.signature-row { display: grid; grid-template-columns: 1fr 1fr; gap: 30px; margin: 30px 0 20px; }
.signature-box { border-top: 1px solid var(--el-text-color-regular); min-height: 50px; }
.signature-box p { text-align: center; font-size: 13px; }
@media (width <= 600px) { .signature-row { grid-template-columns: 1fr; gap: 20px; } }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }
</style>
