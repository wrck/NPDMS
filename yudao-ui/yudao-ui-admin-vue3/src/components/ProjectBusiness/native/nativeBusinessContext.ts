import { computed, inject, onBeforeUnmount, onMounted, type Component, type InjectionKey, type Ref } from 'vue'
import type { BusinessId } from '@/api/pms/platform/business'
import type { NativeBusinessBridge } from './nativeBusinessBridge'

export type NativeValues = Record<string, any>
export interface NativeFormHandle {
  kind?: string
  buildInput: () => Promise<NativeValues>
  requestLeave: () => Promise<boolean>
}
export interface NativeBusinessContext {
  rootKind: string
  bridge: NativeBusinessBridge
  getBridge: (kind?: string) => NativeBusinessBridge
  projectId: () => BusinessId | undefined
  initialEntityId: (kind: string) => BusinessId | undefined
  changed: () => void
  can: (action: string, kind?: string) => boolean
  api: <T extends object>(kind: string, original: T) => T
  Form: Component
  Attachment: Component
  registerForm: (handle: NativeFormHandle) => () => void
  formInput: (kind?: string) => Promise<NativeValues>
  requestLeave: (kind?: string) => Promise<boolean>
}
export const nativeBusinessKey: InjectionKey<NativeBusinessContext> = Symbol('native-project-business')
export const useNativeBusiness = () => inject(nativeBusinessKey, undefined)
/** The original route keeps its API; only the default-business host supplies an adapter. */
export const useNativeBusinessApi = <T extends object>(kind: string, original: T): T =>
  useNativeBusiness()?.api(kind, original) ?? original

export const useNativeBusinessEntry = (kind: string, open: (id: BusinessId) => Promise<unknown>) => {
  const context = useNativeBusiness()
  onMounted(async () => {
    const id = context?.initialEntityId(kind)
    if (id == null) return
    try { await open(id) }
    catch (error) { context!.getBridge(kind).state.error.value = error instanceof Error ? error.message : '业务记录读取失败，请重试' }
  })
}
export const useNativeBusinessLeave = (requestLeave: () => Promise<boolean>, kind?: string) => {
  const unregister = useNativeBusiness()?.registerForm({ kind, buildInput: async () => ({}), requestLeave })
  onBeforeUnmount(() => unregister?.())
}

/** Only user-triggered closes go through this value; a successful save can close the original ref. */
export const useNativeBusinessDialog = (kind: string, visible: Ref<boolean>, busy: () => boolean = () => false) => {
  const context = useNativeBusiness()
  let closing = false
  return computed({ get: () => visible.value, set: async (value: boolean) => {
    if (busy() || closing) return
    if (!value && context) {
      closing = true
      try { if (await context.requestLeave(kind)) visible.value = false } finally { closing = false }
    } else visible.value = value
  } })
}
