<template>
  <SystemUserSchema
    v-if="presentation === 'form-create'"
    v-bind="$attrs"
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event as T)"
    @change="emit('change', $event)"
  />
  <SystemUserScoped
    v-else-if="scopedSource"
    v-bind="$attrs"
    :scoped-source="scopedSource"
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event as T)"
    @change="emit('change', $event)"
    @loaded="emit('loaded', $event)"
  />
  <SystemUserTransferDialog
    v-else-if="presentation === 'transfer'"
    ref="transfer"
    v-bind="$attrs"
    @confirm="(id, rows) => emit('confirm', id, rows)"
  />
  <SystemUserDialogInput
    v-else-if="presentation === 'dialog'"
    v-bind="$attrs"
    :model-value="modelValue as any"
    @update:model-value="emit('update:modelValue', $event as T)"
    @change="emit('change', $event)"
  />
  <SystemUserDropdown
    v-else
    v-bind="$attrs"
    :model-value="modelValue as any"
    @update:model-value="emit('update:modelValue', $event as T)"
    @change="emit('change', $event)"
    @loaded="emit('loaded', $event)"
    ><template #prefix-options><slot name="prefix-options"></slot></template
  ></SystemUserDropdown>
</template>
<script
  setup
  lang="ts"
  generic="T extends string | number | (string | number)[] = string | number | (string | number)[]"
>
import { ref, defineAsyncComponent, watch, onBeforeUnmount } from 'vue'
import SystemUserScoped from './SystemUserScoped.vue'
import type { ScopedUserSelectionSource } from '@/api/system/user/selection'
import SystemUserDropdown from './SystemUserDropdown.vue'
const SystemUserSchema = defineAsyncComponent(() =>
  import('./SystemUserSchema.vue').then((module) => module.default)
)
const SystemUserTransferDialog = defineAsyncComponent(() =>
  import('./SystemUserTransferDialog.vue').then((module) => module.default)
)
const SystemUserDialogInput = defineAsyncComponent(() =>
  import('./SystemUserDialogInput.vue').then((module) => module.default)
)
defineOptions({ name: 'SystemUserSelect', inheritAttrs: false })
defineSlots<{ 'prefix-options'?: () => any }>()
withDefaults(
  defineProps<{
    modelValue?: T
    scopedSource?: ScopedUserSelectionSource
    presentation?: 'dropdown' | 'dialog' | 'transfer' | 'form-create'
  }>(),
  {
    presentation: 'dropdown'
  }
)
const emit = defineEmits<{
  'update:modelValue': [value: T | undefined]
  change: [value: any]
  loaded: [value: any[]]
  confirm: [id: any, rows: any[]]
}>()
const transfer = ref<{ open: (id: number, selectedList?: any[]) => Promise<void> }>()
let pendingOpen:
  | { id: number; selectedList?: any[]; resolve: () => void; reject: (error: unknown) => void }
  | undefined
watch(
  transfer,
  (value) => {
    if (value && pendingOpen) {
      const pending = pendingOpen
      pendingOpen = undefined
      Promise.resolve(value.open(pending.id, pending.selectedList)).then(
        pending.resolve,
        pending.reject
      )
    }
  },
  { flush: 'post' }
)
onBeforeUnmount(() => {
  pendingOpen?.resolve()
  pendingOpen = undefined
})
defineExpose({
  open: (id: number, selectedList?: any[]) => {
    if (transfer.value) return transfer.value.open(id, selectedList)
    pendingOpen?.resolve()
    return new Promise<void>((resolve, reject) => {
      pendingOpen = { id, selectedList, resolve, reject }
    })
  }
})
</script>
