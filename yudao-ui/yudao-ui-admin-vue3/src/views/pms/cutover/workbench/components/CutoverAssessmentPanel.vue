<template>
  <section class="cut-card" aria-labelledby="assessment-heading">
    <header class="cut-card__header">
      <h3 id="assessment-heading"><Icon icon="ep:data-analysis" />割接等级评估问卷</h3>
      <small>人工确定割接等级</small>
    </header>
    <div class="cut-card__body">
      <el-form label-position="top" :disabled="!editable || saving || submitting">
        <div v-for="question in questions" :key="question.key" class="question-row">
          <div><label>{{ question.number }} {{ question.label }}</label><p>{{ question.hint }}</p></div>
          <el-radio-group :model-value="model.answers[question.key] ?? undefined" :aria-label="question.label" @update:model-value="model.answers[question.key] = $event as string">
            <el-radio v-for="item in levelOptions" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
        </div>
        <div class="question-row">
          <div><label>④ 备件是否申请</label><p>是否已申请备件保障</p></div>
          <el-radio-group :model-value="model.answers.sparePartApplied ?? undefined" aria-label="备件是否申请"
            @update:model-value="model.answers.sparePartApplied = $event as boolean">
            <el-radio :value="true">是</el-radio><el-radio :value="false">否</el-radio>
          </el-radio-group>
        </div>
        <div class="question-row">
          <div><label>⑤ 客户服务等级</label><p>项目客户信息带入</p></div>
          <span class="customer-level">{{ customerServiceLevel || '未配置' }}</span>
        </div>
        <div class="grade-result">
          <label>割接等级结果（A/B/C/D）<span class="cut-required"> *</span></label>
          <el-radio-group :model-value="model.manualGrade ?? undefined" aria-label="割接等级结果"
            @update:model-value="model.manualGrade = $event as ManualGrade">
            <el-radio v-for="grade in ['A', 'B', 'C', 'D']" :key="grade" :value="grade">{{ grade }}</el-radio>
          </el-radio-group>
          <p v-if="model.manualGrade" class="cut-help">{{ gradeDestination(model.manualGrade) }}</p>
        </div>
      </el-form>
      <el-alert v-if="model.manualGrade === 'D'" title="D级使用简易流程，跳过调研及风险考察，进入割接方案。" type="warning" :closable="false" show-icon />
    </div>
    <footer v-if="editable" class="cut-actions cut-actions--sticky">
      <el-button :loading="saving" :disabled="submitting" @click="$emit('save')"><Icon icon="ep:document-checked" />保存</el-button>
      <el-button v-if="submittable" type="primary" :loading="submitting" :disabled="saving" @click="$emit('submit')">下一步<Icon icon="ep:arrow-right" /></el-button>
    </footer>
  </section>
</template>
<script setup lang="ts">
import type { AssessmentAnswers, ManualGrade } from '@/api/pms/cutover/workbench'
import { gradeDestination } from '../cutoverTaskInteraction'

defineProps<{
  model: { answers: AssessmentAnswers; manualGrade: ManualGrade | null }
  customerServiceLevel?: string | null
  editable: boolean
  submittable: boolean
  saving: boolean
  submitting: boolean
}>()
defineEmits<{ save: []; submit: [] }>()

const questions = [
  { number: '①', label: '业务重要等级', hint: '评估业务对客户的重要程度', key: 'businessImportanceLevel' },
  { number: '②', label: '操作复杂度等级', hint: '评估割接操作的技术复杂度', key: 'operationComplexityLevel' },
  { number: '③', label: '隐患风险等级', hint: '评估割接操作可能引入的风险', key: 'hiddenRiskLevel' }
] as const

const levelOptions = [
  { label: '低', value: 'LOW' },
  { label: '中', value: 'MEDIUM' },
  { label: '高', value: 'HIGH' }
]
</script>

<style scoped>
.question-row { display: flex; align-items: center; justify-content: space-between; gap: 24px; min-height: 60px; padding: 10px 16px; border-bottom: 1px solid #f0f2f5; }
.question-row label { color: #333; font-size: 13px; }
.question-row p { margin: 0; color: #999; font-size: 12px; }
.customer-level { padding-right: 16px; color: #555; }
.grade-result { display: grid; gap: 6px; padding-top: 14px; }
.grade-result > label { color: #888; font-size: 12px; }
@media (width <= 767px) { .question-row { align-items: flex-start; flex-direction: column; gap: 6px; padding: 10px 0; } }
</style>
