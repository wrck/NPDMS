<template>
  <el-drawer v-model="visible" :size="narrow ? '100%' : '860px'" title="需求分析版本对比">
    <el-skeleton v-if="loading" :rows="7" animated />
    <el-alert v-else-if="errorText" :title="errorText" type="error" :closable="false" show-icon />
    <template v-else-if="comparison">
      <div class="compare-summary">
        <strong>V{{ comparison.sourceBusinessVersion }}</strong>
        <Icon icon="ep:right" />
        <strong>V{{ comparison.targetBusinessVersion }}</strong>
      </div>
      <el-empty v-if="!comparison.fields.length" description="两个版本没有可对比字段" />
      <div v-else class="diff-list">
        <article v-for="field in comparison.fields" :key="field.fieldKey" class="diff-card">
          <header>
            <strong>{{ field.fieldLabel || field.fieldKey }}</strong>
            <el-tag :type="changeTagType(field.changeType)" size="small">
              {{ changeLabel(field.changeType) }}
            </el-tag>
          </header>
          <div class="value-compare">
            <section>
              <h5>V{{ comparison.sourceBusinessVersion }}</h5>
              <div class="value-content">{{ displayValue(field.sourceValue) }}</div>
            </section>
            <section>
              <h5>V{{ comparison.targetBusinessVersion }}</h5>
              <div class="value-content">{{ displayValue(field.targetValue) }}</div>
            </section>
          </div>
          <small v-if="field.controlledFilesChanged" class="file-change">受控文件事实已变化</small>
        </article>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import * as RequirementAnalysisApi from '@/api/pms/engineering/requirement-analysis'
import type {
  RequirementAnalysisCompareVO,
  RequirementAnalysisFieldDiffVO
} from '@/api/pms/engineering/requirement-analysis'

const narrow = useMediaQuery('(max-width: 767px)')
const visible = ref(false)
const loading = ref(false)
const errorText = ref('')
const comparison = ref<RequirementAnalysisCompareVO>()

const open = async (preparationId: number, targetPreparationId: number) => {
  visible.value = true
  loading.value = true
  errorText.value = ''
  comparison.value = undefined
  try {
    comparison.value = await RequirementAnalysisApi.compareVersions(
      preparationId,
      targetPreparationId
    )
  } catch {
    errorText.value = '版本事实已变化或无权查看，请刷新后重试。'
  } finally {
    loading.value = false
  }
}
const displayValue = (value: unknown) => {
  if (value === null || value === undefined || value === '') return '（空）'
  if (typeof value === 'string') {
    try {
      return displayValue(JSON.parse(value))
    } catch {
      return (
        value
          .replace(/<[^>]*>/g, ' ')
          .replace(/\s+/g, ' ')
          .trim() || '（空）'
      )
    }
  }
  if (Array.isArray(value)) return value.join('、') || '（空）'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
const changeLabel = (type: RequirementAnalysisFieldDiffVO['changeType']) =>
  ({ ADDED: '新增', REMOVED: '删除', CHANGED: '已变化', UNCHANGED: '未变化' })[type]
const changeTagType = (type: RequirementAnalysisFieldDiffVO['changeType']) =>
  ({ ADDED: 'success', REMOVED: 'danger', CHANGED: 'warning', UNCHANGED: 'info' })[type] as
    | 'success'
    | 'danger'
    | 'warning'
    | 'info'

defineExpose({ open })
</script>

<style scoped lang="scss">
.compare-summary,
.diff-card header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.compare-summary {
  justify-content: center;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: var(--el-border-radius-base);
}

.diff-list {
  display: grid;
  gap: 12px;
  margin-top: 12px;
}

.diff-card {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.diff-card header {
  justify-content: space-between;
}

.value-compare {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 10px;
}

.value-compare section {
  min-width: 0;
  padding: 10px;
  background: var(--el-fill-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.value-compare h5 {
  margin: 0 0 8px;
}

.value-content {
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.value-compare small {
  display: block;
  margin-top: 8px;
  color: var(--el-text-color-secondary);
}

.file-change {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-color-warning-dark-2);
}

@media (width <= 767px) {
  .value-compare {
    grid-template-columns: 1fr;
  }
}
</style>
