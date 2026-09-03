<template>
  <section aria-labelledby="assessment-heading">
    <div class="section-heading">
      <div>
        <h3 id="assessment-heading">P2 人工分级</h3>
        <p>问卷只保存人工事实，不展示系统建议。</p>
      </div>
      <el-tag v-if="model.manualGrade">人工 {{ model.manualGrade }} 级</el-tag>
    </div>
    <el-form label-position="top" :disabled="!editable">
      <div class="assessment-grid">
        <el-form-item label="业务重要程度">
          <el-select v-model="model.answers.businessImportanceLevel" class="!w-full">
            <el-option v-for="item in levelOptions" :key="item.value" v-bind="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作复杂程度">
          <el-select v-model="model.answers.operationComplexityLevel" class="!w-full">
            <el-option v-for="item in levelOptions" :key="item.value" v-bind="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="潜在风险程度">
          <el-select v-model="model.answers.hiddenRiskLevel" class="!w-full">
            <el-option v-for="item in levelOptions" :key="item.value" v-bind="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否已申请备件">
          <el-radio-group
            :model-value="model.answers.sparePartApplied ?? undefined"
            @update:model-value="model.answers.sparePartApplied = $event as boolean"
          >
            <el-radio :value="true">是</el-radio><el-radio :value="false">否</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="人工等级">
          <el-radio-group
            :model-value="model.manualGrade ?? undefined"
            @update:model-value="model.manualGrade = $event as ManualGrade"
          >
            <el-radio-button v-for="grade in ['A', 'B', 'C', 'D']" :key="grade" :value="grade">{{ grade }}</el-radio-button>
          </el-radio-group>
          <p class="grade-hint">{{ gradeDestination(model.manualGrade) }}</p>
        </el-form-item>
      </div>
    </el-form>
    <div v-if="editable" class="panel-actions">
      <el-button :loading="saving" @click="$emit('save')">保存草稿</el-button>
      <el-button v-if="submittable" type="primary" :loading="submitting" @click="$emit('submit')">提交人工分级</el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { AssessmentAnswers, ManualGrade } from '@/api/pms/cutover/cutover-task'
import { gradeDestination } from '../cutoverTaskInteraction'

defineProps<{
  model: { answers: AssessmentAnswers; manualGrade: ManualGrade | null }
  editable: boolean
  submittable: boolean
  saving: boolean
  submitting: boolean
}>()
defineEmits<{ save: []; submit: [] }>()

const levelOptions = [
  { label: '低', value: 'LOW' },
  { label: '中', value: 'MEDIUM' },
  { label: '高', value: 'HIGH' }
]
</script>

<style scoped>
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.section-heading h3 { margin: 0; }
.section-heading p, .grade-hint { margin: 4px 0 0; color: var(--el-text-color-secondary); }
.assessment-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 20px; margin-top: 16px; }
.panel-actions { display: flex; justify-content: flex-end; gap: 8px; }
@media (max-width: 767px) { .assessment-grid { grid-template-columns: 1fr; } }
</style>
