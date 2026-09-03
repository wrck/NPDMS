<template>
  <section class="closure-form">
    <h3>割接关闭记录</h3>
    <div class="result-grid">
      <div v-for="item in resultItems" :key="item.key" class="result-card">
        <strong>{{ item.label }}</strong>
        <el-radio-group
          :model-value="modelValue[item.normalKey] ?? undefined"
          :disabled="!editable"
          :data-testid="`closure-${item.key}-normal`"
          @update:model-value="update(item.normalKey, $event)"
        >
          <el-radio-button :value="true">正常</el-radio-button>
          <el-radio-button :value="false">异常</el-radio-button>
        </el-radio-group>
        <el-input
          :model-value="modelValue[item.detailKey]"
          :disabled="!editable"
          type="textarea"
          :rows="3"
          :placeholder="`${item.label}说明`"
          :data-testid="`closure-${item.key}-detail`"
          @update:model-value="update(item.detailKey, $event)"
        />
      </div>
    </div>

    <div class="rollback-card">
      <strong>是否发生回退</strong>
      <el-radio-group
        :model-value="modelValue.rollbackOccurred ?? undefined"
        :disabled="!editable"
        data-testid="closure-rollback-occurred"
        @update:model-value="update('rollbackOccurred', $event)"
      >
        <el-radio-button :value="false">未回退</el-radio-button>
        <el-radio-button :value="true">已回退</el-radio-button>
      </el-radio-group>
      <template v-if="modelValue.rollbackOccurred">
        <el-radio-group
          :model-value="modelValue.rollbackSuccessful ?? undefined"
          :disabled="!editable"
          data-testid="closure-rollback-successful"
          @update:model-value="update('rollbackSuccessful', $event)"
        >
          <el-radio-button :value="true">回退成功</el-radio-button>
          <el-radio-button :value="false">回退失败</el-radio-button>
        </el-radio-group>
        <el-input
          :model-value="modelValue.rollbackReason"
          :disabled="!editable"
          type="textarea"
          :rows="3"
          placeholder="回退原因和处置说明"
          data-testid="closure-rollback-reason"
          @update:model-value="update('rollbackReason', $event)"
        />
      </template>
    </div>

    <el-input
      :model-value="modelValue.legacyItems"
      :disabled="!editable"
      type="textarea"
      :rows="3"
      placeholder="遗留事项（可选）"
      data-testid="closure-legacy-items"
      @update:model-value="update('legacyItems', $event)"
    />

    <section class="attachment-section">
      <h4>关闭附件</h4>
      <div v-for="slot in attachmentSlots" :key="slot.purposeCode" class="attachment-row">
        <span>{{ slot.label }}</span>
        <PmsFileUploader
          v-if="editable && closureId !== null"
          :data-testid="`closure-file-${slot.purposeCode}`"
          owner-context="CUT"
          object-type="CUTOVER_CLOSURE"
          :object-id="String(closureId)"
          :purpose-code="slot.purposeCode"
          :reference-key="`cutover-closure-${closureId}-${slot.referenceSuffix}`"
          category-code="CUTOVER_CLOSURE"
          @completed="completeUpload(slot.purposeCode, $event)"
        />
        <small>{{ closureId === null ? '请先创建关闭草稿' : attachment(slot.purposeCode)?.referenceKey || '未选择' }}</small>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import * as FileApi from '@/api/pms/platform/file'
import type { CutoverClosureContent, CutoverClosureFileFact, CutoverClosureFilePurpose, WireLong } from '@/api/pms/cutover/cutover-task'
import { PmsFileUploader } from '@/components/PmsFileArtifact'
import type { FileSelection } from '@/components/PmsFileArtifact'

const props = defineProps<{ modelValue: CutoverClosureContent; closureId: WireLong | null; editable: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: CutoverClosureContent] }>()

type BooleanKey = 'preCheckNormal' | 'executionNormal' | 'testNormal' | 'rollbackOccurred' | 'rollbackSuccessful'
type StringKey = 'preCheckDetail' | 'executionDetail' | 'testDetail' | 'rollbackReason' | 'legacyItems'
const resultItems: Array<{ key: string; label: string; normalKey: BooleanKey; detailKey: StringKey }> = [
  { key: 'precheck', label: '事前检查', normalKey: 'preCheckNormal', detailKey: 'preCheckDetail' },
  { key: 'execution', label: '割接执行', normalKey: 'executionNormal', detailKey: 'executionDetail' },
  { key: 'test', label: '业务测试', normalKey: 'testNormal', detailKey: 'testDetail' }
]
const attachmentSlots: Array<{ purposeCode: Exclude<CutoverClosureFilePurpose, 'MANUAL_COLLECTION_RESULT'>; label: string; referenceSuffix: string }> = [
  { purposeCode: 'POST_COLLECTION_CHECKLIST', label: '割接后检查清单', referenceSuffix: 'post-checklist' },
  { purposeCode: 'IMPLEMENTATION_COMMITMENT', label: '实施承诺', referenceSuffix: 'commitment' },
  { purposeCode: 'OTHER_EVIDENCE', label: '其他证据', referenceSuffix: 'other' }
]

const update = (key: BooleanKey | StringKey, value: unknown) => {
  const next = { ...props.modelValue, [key]: value }
  if (key === 'rollbackOccurred' && value === false) {
    next.rollbackSuccessful = null
    next.rollbackReason = null
  }
  emit('update:modelValue', next)
}
const attachment = (purposeCode: CutoverClosureFilePurpose) =>
  props.modelValue.attachments.find((row) => row.purposeCode === purposeCode)
const fileKey = (purposeCode: CutoverClosureFilePurpose, referenceKey: string) => ({
  ownerContext: 'CUT', objectType: 'CUTOVER_CLOSURE', objectId: String(props.closureId), purposeCode, referenceKey
})
const completeUpload = async (purposeCode: CutoverClosureFilePurpose, selection: FileSelection) => {
  const key = fileKey(purposeCode, selection.referenceKey)
  const artifact = await FileApi.getArtifact(selection.artifactId, key)
  const versions = await FileApi.getVersions(selection.artifactId, { ...key, pageSize: 20 })
  const version = versions.items.find((row) => row.versionNo === selection.versionNo)
  if (!version) throw new Error('PLT 未返回刚完成的关闭附件版本')
  const fact: CutoverClosureFileFact = {
    purposeCode, artifactId: selection.artifactId, versionNo: selection.versionNo,
    referenceKey: selection.referenceKey, scopeVersion: artifact.reference.scopeVersion,
    sha256: version.sha256,
    fileFactVersion: {
      artifactVersion: artifact.artifactVersion,
      referenceVersion: artifact.reference.referenceVersion,
      availabilityVersion: version.availabilityVersion
    }
  }
  const attachments = props.modelValue.attachments.filter((row) => row.purposeCode !== purposeCode)
  attachments.push(fact)
  attachments.sort((a, b) => a.purposeCode.localeCompare(b.purposeCode) || a.referenceKey.localeCompare(b.referenceKey))
  emit('update:modelValue', { ...props.modelValue, attachments })
}
</script>

<style scoped>
.closure-form { min-width: 0; }
.result-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.result-card, .rollback-card, .attachment-section { display: grid; gap: 10px; padding: 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; }
.rollback-card, .attachment-section { margin-top: 14px; }
.attachment-row { display: grid; grid-template-columns: minmax(140px, 1fr) auto minmax(120px, 1fr); align-items: center; gap: 10px; }
@media (max-width: 1023px) { .result-grid { grid-template-columns: 1fr; } }
@media (max-width: 767px) { .attachment-row { grid-template-columns: 1fr; } }
</style>
