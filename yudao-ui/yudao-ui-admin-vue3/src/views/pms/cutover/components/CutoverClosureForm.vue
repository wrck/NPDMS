<template>
  <section class="closure-form" aria-label="割接闭环填写">
    <div v-for="(item, index) in resultItems" :key="item.key" class="closure-question">
      <label class="question-label">{{ index + 1 }}. {{ item.label }}是否正常？<span class="cut-required"> *</span></label>
      <el-radio-group :model-value="modelValue[item.normalKey] ?? undefined" :disabled="!editable"
        :data-testid="'closure-' + item.key + '-normal'" :aria-label="item.label + '是否正常'"
        @update:model-value="update(item.normalKey, $event)">
        <el-radio :value="true">是</el-radio><el-radio :value="false">否</el-radio>
      </el-radio-group>
      <div v-if="modelValue[item.normalKey] === false || modelValue[item.detailKey]" class="reason-box">
        <label>详细说明<span v-if="modelValue[item.normalKey] === false" class="cut-required"> *</span></label>
        <el-input :model-value="modelValue[item.detailKey]" :disabled="!editable" type="textarea" :rows="2"
          :placeholder="'请填写' + item.label + '情况及处置说明'" :data-testid="'closure-' + item.key + '-detail'"
          @update:model-value="update(item.detailKey, $event)" />
      </div>
    </div>
    <div class="closure-question">
      <label class="question-label">4. 割接是否存在回退操作？<span class="cut-required"> *</span></label>
      <el-radio-group :model-value="modelValue.rollbackOccurred ?? undefined" :disabled="!editable"
        data-testid="closure-rollback-occurred" aria-label="是否存在回退操作" @update:model-value="update('rollbackOccurred', $event)">
        <el-radio :value="true">是，有回退</el-radio><el-radio :value="false">否，无回退</el-radio>
      </el-radio-group>
      <div v-if="modelValue.rollbackOccurred" class="reason-box">
        <label>回退结果及原因<span class="cut-required"> *</span></label>
        <el-radio-group :model-value="modelValue.rollbackSuccessful ?? undefined" :disabled="!editable"
          data-testid="closure-rollback-successful" aria-label="回退结果" @update:model-value="update('rollbackSuccessful', $event)">
          <el-radio :value="true">回退成功</el-radio><el-radio :value="false">回退失败</el-radio>
        </el-radio-group>
        <el-input :model-value="modelValue.rollbackReason" :disabled="!editable" type="textarea" :rows="2"
          placeholder="请填写回退原因及处置说明" data-testid="closure-rollback-reason" @update:model-value="update('rollbackReason', $event)" />
      </div>
    </div>
    <section class="attachment-section" aria-label="闭环附件">
      <div v-for="(slot, index) in attachmentSlots" :key="slot.purposeCode" class="attachment-row">
        <h4 class="cut-section-title">{{ index < 2 ? (index + 5) + '. ' : '' }}{{ slot.label }}<span v-if="index < 2" class="cut-required"> *</span></h4>
        <PmsFileUploader ref="uploadRefs" v-if="editable && closureId !== null"
          accept=".pdf,.doc,.docx,.xls,.xlsx,.png,.jpg,.jpeg"
          :data-testid="'closure-file-' + slot.purposeCode" owner-context="CUT" object-type="CUTOVER_CLOSURE" :object-id="String(closureId)"
          :purpose-code="slot.purposeCode" :reference-key="'cutover-closure-' + closureId + '-' + slot.referenceSuffix"
          category-code="CUTOVER_CLOSURE" @completed="completeUpload(slot.purposeCode, $event)" />
        <p v-if="editable && closureId !== null" class="cut-help">支持PDF、Word、Excel及图片，单文件不超过50MB。</p>
        <PmsFileVersionReferenceList v-if="attachment(slot.purposeCode)" owner-context="CUT" object-type="CUTOVER_CLOSURE"
          :object-id="String(closureId)" :purpose-code="slot.purposeCode" :reference-key="attachment(slot.purposeCode)!.referenceKey"
          :artifact-id="attachment(slot.purposeCode)!.artifactId" :version-no="attachment(slot.purposeCode)!.versionNo" />
        <p v-else-if="closureId === null" class="cut-help">先保存闭环草稿，即可上传{{ slot.label }}。</p>
        <p v-else-if="!editable" class="cut-help">未上传附件</p>
        <el-button v-if="editable && closureId !== null && !attachment(slot.purposeCode)" :disabled="pendingUploads"
          link type="primary" :data-testid="'closure-restore-' + slot.purposeCode" @click="restoreUpload(slot)">读取已上传附件</el-button>
      </div>
    </section>
    <h4 class="cut-section-title">7. 割接遗留项</h4>
    <el-input :model-value="modelValue.legacyItems" :disabled="!editable" type="textarea" :rows="3"
      placeholder="请输入割接遗留事项及后续安排，无遗留事项可填写“无”" data-testid="closure-legacy-items" @update:model-value="update('legacyItems', $event)" />
  </section>
</template>
<script setup lang="ts">
import * as FileApi from '@/api/pms/platform/file/version-reference'
import { useMessage } from '@/hooks/web/useMessage'
import type { CutoverClosureContent, CutoverClosureFileFact, CutoverClosureFilePurpose, WireLong } from '@/api/pms/cutover'
import { PmsFileUploader } from '@/components/PmsFileArtifact'
import PmsFileVersionReferenceList from '@/components/PmsFileArtifact/PmsFileVersionReferenceList.vue'

const props = defineProps<{ modelValue: CutoverClosureContent; closureId: WireLong | null; editable: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: CutoverClosureContent]
  'attachment-completed': [fact: CutoverClosureFileFact]
  'upload-pending': [pending: boolean]
}>()
const uploadRefs = ref<Array<InstanceType<typeof PmsFileUploader>>>([])
const completingUploads = ref(0)
const message = useMessage()
const pendingUploads = computed(() => completingUploads.value > 0 ||
  uploadRefs.value.some(uploader => uploader.isBusy() || uploader.hasPendingFile()))
watch(pendingUploads, pending => emit('upload-pending', pending), { immediate: true })
defineExpose({ hasPendingUploads: () => pendingUploads.value })

type BooleanKey = 'preCheckNormal' | 'executionNormal' | 'testNormal' | 'rollbackOccurred' | 'rollbackSuccessful'
type StringKey = 'preCheckDetail' | 'executionDetail' | 'testDetail' | 'rollbackReason' | 'legacyItems'
const resultItems: Array<{ key: string; label: string; normalKey: BooleanKey; detailKey: StringKey }> = [
  { key: 'precheck', label: '割接前检查', normalKey: 'preCheckNormal', detailKey: 'preCheckDetail' },
  { key: 'execution', label: '割接执行', normalKey: 'executionNormal', detailKey: 'executionDetail' },
  { key: 'test', label: '割接测试', normalKey: 'testNormal', detailKey: 'testDetail' }
]
const attachmentSlots: Array<{ purposeCode: Exclude<CutoverClosureFilePurpose, 'MANUAL_COLLECTION_RESULT'>; label: string; referenceSuffix: string }> = [
  { purposeCode: 'POST_COLLECTION_CHECKLIST', label: '割接后信息采集清单', referenceSuffix: 'post-checklist' },
  { purposeCode: 'IMPLEMENTATION_COMMITMENT', label: '割接实施承诺书', referenceSuffix: 'commitment' },
  { purposeCode: 'OTHER_EVIDENCE', label: '其他附件（可选）', referenceSuffix: 'other' }
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
const completeUpload = async (purposeCode: CutoverClosureFilePurpose,
    selection: Pick<CutoverClosureFileFact, 'artifactId' | 'versionNo' | 'referenceKey'>) => {
  completingUploads.value++
  try {
    const key = fileKey(purposeCode, selection.referenceKey)
    const artifact = await FileApi.getArtifact(selection.artifactId, key)
    const versions = await FileApi.getVersions(selection.artifactId, { ...key, pageSize: 20 })
    const version = versions.items.find((row) => row.versionNo === selection.versionNo)
    if (!version) throw new Error('PLT 未返回刚完成的闭环附件版本')
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
    emit('attachment-completed', fact)
  } finally {
    completingUploads.value--
  }
}
const restoreUpload = async (slot: typeof attachmentSlots[number]) => {
  completingUploads.value++
  try {
    const key = fileKey(slot.purposeCode, `cutover-closure-${props.closureId}-${slot.referenceSuffix}`)
    const reference = await FileApi.getCurrentReference(key)
    if (!reference || reference.status !== 'ACTIVE') {
      message.warning('未找到已绑定附件，请先完成上传')
      return
    }
    await completeUpload(slot.purposeCode, reference)
  } finally {
    completingUploads.value--
  }
}
</script>

<style scoped>
.closure-form { min-width: 0; }
.closure-question { padding: 0 0 16px; margin-bottom: 16px; border-bottom: 1px solid #f0f2f5; }
.question-label { display: block; margin-bottom: 6px; color: #333; font-size: 13px; font-weight: 500; }
.reason-box { display: grid; gap: 6px; margin-top: 10px; padding: 12px; border-radius: 4px; background: #fafafa; }
.reason-box > label { color: #666; font-size: 12px; }
.attachment-row { margin-top: 20px; }
.attachment-row :deep(.pms-file-uploader) { padding: 12px; border: 1px dashed #d9d9d9; border-radius: 4px; background: #fafafa; }
.attachment-row :deep(.pms-file-uploader .uploader-form) { display: flex; flex-wrap: wrap; gap: 8px; }
</style>
