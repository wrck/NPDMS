<template>
  <div v-hasPermi="['pms:file:upload']">
    <el-button :disabled="disabled || busy || pending.length > 0" @click="input?.click()">添加多个拓扑附件</el-button>
    <input ref="input" type="file" multiple hidden :accept="accept" :disabled="disabled || busy" @change="selectFiles" />
    <p v-if="busy" role="status">正在上传 {{ pending[0]?.file.name }}，剩余 {{ pending.length }} 份</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <el-button v-if="pending.length && !busy" :disabled="disabled" @click="upload">重试剩余附件</el-button>
    <el-button v-if="pending.length && !busy" :disabled="disabled" @click="discard">取消剩余选择</el-button>
  </div>
</template>
<script setup lang="ts">
import { generateUUID } from '@/utils'
import * as FileApi from '@/api/pms/platform/file'
import type { FileSelection } from '@/components/PmsFileArtifact'
import type { JsonObject } from '@/api/pms/platform/dynamic-form'
import { fileUploadMediaType } from '@/components/PmsFileArtifact/fileMediaType'
import { registerDraftUpload } from '@/hooks/web/useDraftUploads'
const props = defineProps<{ revisionId: string; disabled: boolean; accept: string; ownerExecutionContext?: JsonObject }>()
const emit = defineEmits<{ completed: [selection: FileSelection]; busy: [value: boolean] }>()
type Pending = { file: File; referenceKey: string; initKey: string; completeKey: string; initialized?: FileApi.FileUploadInitRespVO }
const input = ref<HTMLInputElement>()
const pending = ref<Pending[]>([])
const busy = ref(false)
const error = ref('')
let active = true
let selectedRevision = ''
let execution: JsonObject | undefined
const discard = () => { if (!busy.value) { pending.value = []; error.value = '' } }
registerDraftUpload({ isBusy: () => busy.value, hasPendingFile: () => !!pending.value.length, discard })
watch(busy, value => emit('busy', value), { flush: 'sync' })
onBeforeUnmount(() => { active = false; emit('busy', false) })
const selectFiles = () => {
  if (props.disabled || busy.value || pending.value.length) return
  const files = Array.from(input.value?.files || [])
  if (input.value) input.value.value = ''
  if (!files.length) return
  if (files.some(file => file.size <= 0 || file.size > 50 * 1024 * 1024)) {
    error.value = '每份文件大小必须大于0且不超过50MB，本次未开始上传。'
    return
  }
  selectedRevision = props.revisionId
  execution = props.ownerExecutionContext ? JSON.parse(JSON.stringify(props.ownerExecutionContext)) : undefined
  pending.value = files.map(file => ({ file, referenceKey: generateUUID(), initKey: generateUUID(), completeKey: generateUUID() }))
  void upload()
}
const upload = async () => {
  if (props.disabled || busy.value) return
  busy.value = true
  error.value = ''
  try {
    while (pending.value.length && active) {
      if (props.disabled || props.revisionId !== selectedRevision) throw new Error('需求版本已切换或不可编辑，请取消剩余选择。')
      const entry = pending.value[0]
      entry.initialized ||= await FileApi.initializeUpload({
        ownerContext: 'SOL', objectType: 'REQUIREMENT_ANALYSIS_REVISION', objectId: selectedRevision,
        purposeCode: 'FORM_FIELD_ATTACHMENT/NETWORK_TOPOLOGY__ATTACHMENTS', referenceKey: entry.referenceKey,
        categoryCode: 'DYNAMIC_FORM_ATTACHMENT', modeCode: 'CREATE_ARTIFACT', ownerExecutionContext: execution,
        fileName: entry.file.name, declaredSizeBytes: entry.file.size, declaredMediaType: fileUploadMediaType(entry.file)
      }, entry.initKey)
      if (!active || props.disabled || props.revisionId !== selectedRevision) return
      const completed = await FileApi.completeUpload(entry.initialized.artifactId, entry.initialized.sessionId, entry.file, entry.completeKey)
      if (!active || props.disabled || props.revisionId !== selectedRevision) return
      emit('completed', completed)
      pending.value.shift()
      await nextTick()
    }
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '附件上传未完成，请重试。' }
  finally { busy.value = false }
}
</script>
