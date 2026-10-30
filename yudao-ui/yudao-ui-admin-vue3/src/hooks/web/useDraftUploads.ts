import { computed, inject, onBeforeUnmount, provide, shallowReactive, type InjectionKey } from 'vue'

interface DraftUpload {
  isBusy: () => boolean
  hasPendingFile: () => boolean
  discard: () => void
}
const draftUploadsKey: InjectionKey<Map<symbol, DraftUpload>> = Symbol('draft-uploads')

export const useDraftUploads = () => {
  const uploads = shallowReactive(new Map<symbol, DraftUpload>())
  provide(draftUploadsKey, uploads)
  const busy = computed(() => [...uploads.values()].some(upload => upload.isBusy()))
  const pending = computed(() => [...uploads.values()].some(upload => upload.hasPendingFile()))
  const discard = () => { if (!busy.value) uploads.forEach(upload => upload.discard()) }
  return { busy, pending, discard }
}

export const registerDraftUpload = (upload: DraftUpload) => {
  const uploads = inject(draftUploadsKey, undefined)
  if (!uploads) return
  const key = Symbol('upload')
  uploads.set(key, upload)
  onBeforeUnmount(() => uploads.delete(key))
}
