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
      <el-empty v-if="!comparison.sections.length" description="两个版本没有可对比章节" />
      <div v-else class="diff-list">
        <article
          v-for="section in comparison.sections"
          :key="section.sectionCode"
          class="diff-card"
        >
          <header>
            <strong>{{ sectionName(section.sectionCode) }}</strong>
            <el-tag :type="changeTagType(section.changeType)" size="small">
              {{ changeLabel(section.changeType) }}
            </el-tag>
          </header>
          <div class="value-compare">
            <section>
              <h5>V{{ comparison.sourceBusinessVersion }}</h5>
              <div class="value-content">{{
                displayValue(sourceSection(section.sectionCode)?.valueSnapshot)
              }}</div>
              <small
                >附件 {{ sourceSection(section.sectionCode)?.attachments.length || 0 }} 个</small
              >
              <ul
                v-if="sourceSection(section.sectionCode)?.attachments.length"
                class="attachment-facts"
              >
                <li
                  v-for="file in sourceSection(section.sectionCode)?.attachments"
                  :key="file.referenceKey"
                >
                  {{ file.name || `文件 #${file.artifactId}` }} · V{{ file.versionNo }} ·
                  {{ file.referenceKey }}
                </li>
              </ul>
            </section>
            <section>
              <h5>V{{ comparison.targetBusinessVersion }}</h5>
              <div class="value-content">{{
                displayValue(targetSection(section.sectionCode)?.valueSnapshot)
              }}</div>
              <small
                >附件 {{ targetSection(section.sectionCode)?.attachments.length || 0 }} 个</small
              >
              <ul
                v-if="targetSection(section.sectionCode)?.attachments.length"
                class="attachment-facts"
              >
                <li
                  v-for="file in targetSection(section.sectionCode)?.attachments"
                  :key="file.referenceKey"
                >
                  {{ file.name || `文件 #${file.artifactId}` }} · V{{ file.versionNo }} ·
                  {{ file.referenceKey }}
                </li>
              </ul>
            </section>
          </div>
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
  RequirementAnalysisDetailVO,
  RequirementAnalysisSectionDiffVO
} from '@/api/pms/engineering/requirement-analysis'

const narrow = useMediaQuery('(max-width: 767px)')
const visible = ref(false)
const loading = ref(false)
const errorText = ref('')
const comparison = ref<RequirementAnalysisCompareVO>()
const sourceDetail = ref<RequirementAnalysisDetailVO>()
const targetDetail = ref<RequirementAnalysisDetailVO>()

const open = async (preparationId: number, targetPreparationId: number) => {
  visible.value = true
  loading.value = true
  errorText.value = ''
  comparison.value = undefined
  sourceDetail.value = undefined
  targetDetail.value = undefined
  try {
    const [diff, source, target] = await Promise.all([
      RequirementAnalysisApi.compareVersions(preparationId, targetPreparationId),
      RequirementAnalysisApi.getDetail(preparationId),
      RequirementAnalysisApi.getDetail(targetPreparationId)
    ])
    comparison.value = diff
    sourceDetail.value = source
    targetDetail.value = target
  } catch {
    errorText.value = '版本事实已变化或无权查看，请刷新后重试。'
  } finally {
    loading.value = false
  }
}
const sourceSection = (code: string) =>
  sourceDetail.value?.sections.find((section) => section.sectionCode === code)
const targetSection = (code: string) =>
  targetDetail.value?.sections.find((section) => section.sectionCode === code)
const sectionName = (code: string) =>
  sourceSection(code)?.sectionName || targetSection(code)?.sectionName || code
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
const changeLabel = (type: RequirementAnalysisSectionDiffVO['changeType']) =>
  ({ ADDED: '新增', REMOVED: '删除', CHANGED: '已变化', UNCHANGED: '未变化' })[type]
const changeTagType = (type: RequirementAnalysisSectionDiffVO['changeType']) =>
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

.attachment-facts {
  padding-left: 18px;
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}

@media (width <= 767px) {
  .value-compare {
    grid-template-columns: 1fr;
  }
}
</style>
