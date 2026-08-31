<template>
  <article class="checklist-field" :class="{ required: item.required }">
    <header>
      <div>
        <h4>{{ item.itemName }}<span v-if="item.required" aria-label="必填"> *</span></h4>
        <p v-if="item.itemDescription">{{ item.itemDescription }}</p>
      </div>
      <div class="field-tags">
        <el-tag size="small" effect="plain">{{ sourceLabel }}</el-tag>
        <el-tag v-if="item.currentResult" size="small" type="success">
          {{ resultLabel }}
        </el-tag>
      </div>
    </header>

    <el-select
      v-if="options.length"
      data-testid="checklist-select"
      :model-value="directValue"
      :disabled="readonly"
      clearable
      placeholder="请选择"
      @update:model-value="updateDirect"
    >
      <el-option v-for="option in options" :key="option.value" :label="option.label" :value="option.value" />
    </el-select>
    <el-input
      v-else
      data-testid="checklist-input"
      :model-value="directValue"
      :disabled="readonly"
      :rows="item.interfaceFormatCode === 'TABLE' ? 5 : 3"
      type="textarea"
      placeholder="填写调研结果"
      @update:model-value="updateDirect"
    />

    <details v-if="!readonly" class="manual-area">
      <summary>自动/外部结果不可用时，改用人工证据</summary>
      <el-input v-model="factDescription" placeholder="说明人工核验事实" />
      <PmsFileUploader
        data-testid="manual-uploader"
        owner-context="CUT"
        object-type="CUTOVER_CHECKLIST_ITEM"
        :object-id="String(item.itemId)"
        purpose-code="MANUAL_EVIDENCE"
        :reference-key="`cutover-checklist-${item.stableItemKey}`"
        category-code="CUTOVER_CHECKLIST_MANUAL_EVIDENCE"
        @completed="completeUpload"
      />
    </details>
  </article>
</template>

<script setup lang="ts">
import * as FileApi from '@/api/pms/platform/file'
import type {
  ChecklistFileHandle,
  CutoverChecklistItem
} from '@/api/pms/cutover/cutover-task'
import { PmsFileUploader } from '@/components/PmsFileArtifact'
import type { FileSelection } from '@/components/PmsFileArtifact'

const props = defineProps<{ item: CutoverChecklistItem; directValue: string; readonly: boolean }>()
const emit = defineEmits<{
  direct: [stableItemKey: string, value: string]
  manual: [stableItemKey: string, file: ChecklistFileHandle, factDescription: string]
}>()
const factDescription = ref('')

const sourceLabel = computed(() => (props.item.sourceCode === 'CUSTOM' ? '自定义项' : '配置匹配'))
const resultLabel = computed(() =>
  props.item.currentResult?.resultSourceCode === 'MANUAL' ? '人工证据' : '已填写'
)
const options = computed<Array<{ label: string; value: string }>>(() => {
  if (!props.item.interfaceSchemaSnapshot) return []
  try {
    const schema = JSON.parse(props.item.interfaceSchemaSnapshot)
    return Array.isArray(schema.options)
      ? schema.options
          .map((entry: unknown) =>
            typeof entry === 'string'
              ? { label: entry, value: entry }
              : (entry as { label?: string; value?: string })
          )
          .filter((entry: { label?: string; value?: string }) => entry.label && entry.value)
      : []
  } catch {
    return []
  }
})

const updateDirect = (value: string) => emit('direct', props.item.stableItemKey, value || '')
const fileKey = (referenceKey: string) => ({
  ownerContext: 'CUT',
  objectType: 'CUTOVER_CHECKLIST_ITEM',
  objectId: String(props.item.itemId),
  purposeCode: 'MANUAL_EVIDENCE',
  referenceKey
})
const completeUpload = async (selection: FileSelection) => {
  const artifact = await FileApi.getArtifact(selection.artifactId, fileKey(selection.referenceKey))
  const versions = await FileApi.getVersions(selection.artifactId, {
    ...fileKey(selection.referenceKey),
    pageSize: 20
  })
  const version = versions.items.find((entry) => entry.versionNo === selection.versionNo)
  if (!version) throw new Error('PLT 未返回刚完成的文件版本')
  emit('manual', props.item.stableItemKey, {
    artifactId: selection.artifactId,
    versionNo: selection.versionNo,
    referenceKey: selection.referenceKey,
    scopeVersion: artifact.reference.scopeVersion,
    fileFactVersion: {
      artifactVersion: artifact.artifactVersion,
      referenceVersion: artifact.reference.referenceVersion,
      availabilityVersion: version.availabilityVersion
    }
  }, factDescription.value)
}
</script>

<style scoped>
.checklist-field { padding: 16px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; }
.checklist-field + .checklist-field { margin-top: 12px; }
.checklist-field header { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.checklist-field h4, .checklist-field p { margin: 0; }
.checklist-field h4 span { color: var(--el-color-danger); }
.checklist-field p { margin-top: 4px; color: var(--el-text-color-secondary); }
.field-tags { display: flex; align-items: flex-start; gap: 6px; }
.manual-area { margin-top: 14px; }
.manual-area summary { margin-bottom: 10px; cursor: pointer; color: var(--el-color-primary); }
.manual-area :deep(.pms-file-uploader) { margin-top: 10px; }
@media (max-width: 767px) { .checklist-field header { flex-direction: column; } }
</style>
