<template>
  <div class="checklist-multi-upload" v-hasPermi="['pms:file:upload']">
    <el-button :disabled="disabled || busy || pending.length > 0" @click="input?.click()">添加多个附件</el-button>
    <input ref="input" type="file" multiple hidden :accept="accept" :disabled="disabled || busy" @change="selectFiles" />
    <p v-if="busy" role="status">正在处理 {{ pending[0]?.file.name }}，剩余 {{ pending.length }} 份</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <el-button v-if="pending.length && !busy" :disabled="disabled" @click="upload">重试剩余附件</el-button>
    <el-button v-if="pending.length && !busy" :disabled="disabled" @click="discard">取消剩余选择</el-button>
  </div>
</template>

<script setup lang="ts">
import { generateUUID } from '@/utils'
import * as FileApi from '@/api/pms/platform/file'
import type { FileSelection } from '@/components/PmsFileArtifact'
import { fileUploadMediaType } from '@/components/PmsFileArtifact/fileMediaType'
import { registerDraftUpload } from '@/hooks/web/useDraftUploads'

const props = defineProps<{
  itemId: string
  disabled: boolean
  accept: string
  bindFile: (selection: FileSelection) => Promise<boolean>
}>()
type PendingFile = {
  file: File
  referenceKey: string
  initKey: string
  completeKey: string
  initialized?: FileApi.FileUploadInitRespVO
  completed?: FileSelection
}
const input = ref<HTMLInputElement>()
const pending = ref<PendingFile[]>([])
const busy = ref(false)
const error = ref('')
let active = true
onBeforeUnmount(() => { active = false })
const discard = () => { if (!busy.value) { pending.value = []; error.value = '' } }
registerDraftUpload({ isBusy: () => busy.value, hasPendingFile: () => pending.value.length > 0, discard })

const selectFiles = () => {
  if (props.disabled || busy.value || pending.value.length) return
  const files = Array.from(input.value?.files || [])
  if (input.value) input.value.value = ''
  if (!files.length) return
  if (files.some(file => file.size <= 0 || file.size > 50 * 1024 * 1024)) {
    error.value = '每份文件大小必须大于0且不超过50MB，本次未开始上传。'
    return
  }
  pending.value = files.map(file => ({ file, referenceKey: `cutover-checklist-${generateUUID()}`,
    initKey: generateUUID(), completeKey: generateUUID() }))
  void upload()
}
const upload = async () => {
  if (props.disabled || busy.value) return
  const itemId = props.itemId
  busy.value = true
  error.value = ''
  try {
    while (pending.value.length && active && props.itemId === itemId) {
      if (props.disabled) throw new Error('当前清单不可编辑，未继续上传。')
      const entry = pending.value[0]
      if (!entry.initialized) entry.initialized = await FileApi.initializeUpload({
        ownerContext: 'CUT', objectType: 'CUTOVER_CHECKLIST_ITEM', objectId: itemId,
        purposeCode: 'MANUAL_EVIDENCE', referenceKey: entry.referenceKey,
        categoryCode: 'CUTOVER_CHECKLIST_MANUAL_EVIDENCE', modeCode: 'CREATE_ARTIFACT',
        fileName: entry.file.name, declaredSizeBytes: entry.file.size, declaredMediaType: fileUploadMediaType(entry.file)
      }, entry.initKey)
      if (!active || props.itemId !== itemId) return
      if (!entry.completed) entry.completed = await FileApi.completeUpload(entry.initialized.artifactId,
        entry.initialized.sessionId, entry.file, entry.completeKey)
      if (!active || props.itemId !== itemId) return
      if (!await props.bindFile(entry.completed)) throw new Error('文件已上传，但尚未选入清单；请重试绑定。')
      pending.value.shift()
      await nextTick()
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '附件处理未完成，请重试。'
  } finally { busy.value = false }
}
</script>
