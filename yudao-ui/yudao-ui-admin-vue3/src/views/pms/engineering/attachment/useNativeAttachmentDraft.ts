import { onBeforeUnmount, ref } from 'vue'
import type { UploadFile } from 'element-plus'
import * as FileApi from '@/api/pms/platform/file'
import { generateUUID } from '@/utils'
import { fileUploadMediaType } from '@/components/PmsFileArtifact/fileMediaType'
import { registerDraftUpload } from '@/hooks/web/useDraftUploads'
import { checkPermi } from '@/utils/permission'

interface DraftOptions {
  context: () => { module: string; type: string; purpose: string; accept: string }
  readonly: () => boolean
  collect: () => Promise<boolean>
}
/** Select before the first save; bind only after the Owner has its real identity. */
export const useNativeAttachmentDraft = (options: DraftOptions) => {
  const pendingFiles = ref<UploadFile[]>([]),
    draftBusy = ref(false),
    draftError = ref('')
  const attempts = new Map<
    number,
    {
      initKey: string
      completeKey: string
      referenceKey: string
      initialized?: FileApi.FileUploadInitRespVO
      bound?: boolean
    }
  >()
  let generation = 0
  const selectPending = (file: UploadFile) => {
    if (options.readonly() || draftBusy.value || !file.raw) return
    const accepted = options
      .context()
      .accept.split(',')
      .some((ext) => file.name.toLowerCase().endsWith(ext.toLowerCase()))
    if (!accepted || file.raw.size <= 0 || file.raw.size > 5 * 1024 * 1024) {
      draftError.value = '请选择支持类型且不超过5MiB的非空附件'
      return
    }
    if (pendingFiles.value.length >= 5) {
      draftError.value = '一次最多选择5个附件'
      return
    }
    draftError.value = ''
    if (!pendingFiles.value.some((value) => value.uid === file.uid)) pendingFiles.value.push(file)
  }
  const beforeRemovePending = (file: UploadFile) => {
    if (options.readonly() || draftBusy.value) return false
    if (attempts.get(file.uid)?.initialized) {
      draftError.value = '附件已进入保存，请先重试完成保存，再从附件记录解除引用'
      return false
    }
    return true
  }
  const removePending = (file: UploadFile) => {
    if (!beforeRemovePending(file)) return
    pendingFiles.value = pendingFiles.value.filter((value) => value.uid !== file.uid)
    attempts.delete(file.uid)
  }
  const resetPending = () => {
    ++generation
    pendingFiles.value = []
    attempts.clear()
    draftBusy.value = false
    draftError.value = ''
  }
  const savePending = async (entityId: number | string) => {
    if (!pendingFiles.value.length) return true
    if (options.readonly() || !checkPermi(['pms:file:upload']) || draftBusy.value || !entityId)
      return false
    const currentGeneration = generation,
      owner = { ...options.context() }
    const current = () => currentGeneration === generation && !options.readonly()
    draftBusy.value = true
    draftError.value = ''
    try {
      for (const file of [...pendingFiles.value]) {
        if (!current()) return false
        let attempt = attempts.get(file.uid)
        if (!attempt) {
          attempt = {
            initKey: generateUUID(),
            completeKey: generateUUID(),
            referenceKey: generateUUID()
          }
          attempts.set(file.uid, attempt)
        }
        if (!attempt.bound) {
          attempt.initialized ||= await FileApi.initializeUpload(
            {
              modeCode: 'CREATE_ARTIFACT',
              ownerContext: owner.module,
              objectType: owner.type,
              objectId: String(entityId),
              purposeCode: owner.purpose,
              referenceKey: attempt.referenceKey,
              categoryCode: owner.purpose,
              fileName: file.name,
              declaredSizeBytes: file.raw!.size,
              declaredMediaType: fileUploadMediaType(file.raw!)
            },
            attempt.initKey
          )
          if (!current()) return false
          await FileApi.completeUpload(
            attempt.initialized.artifactId,
            attempt.initialized.sessionId,
            file.raw!,
            attempt.completeKey
          )
          if (!current()) return false
          attempt.bound = true
        }
      }
      if (!current() || !(await options.collect()) || !current()) return false
      pendingFiles.value = []
      attempts.clear()
      return true
    } catch (failure: unknown) {
      if (currentGeneration === generation)
        draftError.value =
          (typeof failure === 'string'
            ? failure
            : failure instanceof Error
              ? failure.message
              : '') || '附件未保存，请重试'
      return false
    } finally {
      if (currentGeneration === generation) draftBusy.value = false
    }
  }
  registerDraftUpload({
    isBusy: () => draftBusy.value,
    hasPendingFile: () => pendingFiles.value.length > 0,
    discard: () => {
      if (!draftBusy.value) resetPending()
    }
  })
  onBeforeUnmount(() => {
    ++generation
  })
  return {
    pendingFiles,
    draftBusy,
    draftError,
    selectPending,
    beforeRemovePending,
    removePending,
    resetPending,
    savePending
  }
}
