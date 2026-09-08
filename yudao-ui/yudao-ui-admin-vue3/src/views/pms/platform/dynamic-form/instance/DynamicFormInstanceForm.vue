<template>
  <el-drawer
    v-model="visible"
    size="100%"
    :title="title"
    :before-close="beforeClose"
    destroy-on-close
  >
    <DynamicFormInstanceContent
      v-if="activeId"
      :key="activeId"
      ref="contentRef"
      :instance-id="activeId"
      @loaded="title = `填写动态表单：${$event.instanceName}`"
      @changed="emit('changed')"
      @dirty-change="emit('dirty-change', $event)"
    />
  </el-drawer>
</template>

<script setup lang="ts">
import DynamicFormInstanceContent from './DynamicFormInstanceContent.vue'
import { sameBusinessViewId, type BusinessViewId } from '@/api/pms/platform/business-view/ids'

// PM-03 / F-PLT-002: drawer chrome only; content/runtime/save are shared with BusinessView.
defineOptions({ name: 'DynamicFormInstanceForm' })
const props = defineProps<{ modelValue: boolean; instanceId?: BusinessViewId }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: []
  'dirty-change': [value: boolean]
}>()
const visible = ref(false)
const activeId = ref<BusinessViewId>()
const title = ref('动态表单实例')
const contentRef = ref<InstanceType<typeof DynamicFormInstanceContent>>()
const requestLeave = async () => (await contentRef.value?.requestLeave()) ?? true
const beforeClose = async (done: () => void) => {
  if (await requestLeave()) {
    done()
    emit('update:modelValue', false)
  }
}
let sequence = 0
watch(
  () => [props.modelValue, props.instanceId] as const,
  async ([open, id]) => {
    const current = ++sequence
    if (
      visible.value &&
      (!open || !sameBusinessViewId(id, activeId.value)) &&
      !(await requestLeave())
    ) {
      emit('update:modelValue', true)
      return
    }
    if (current !== sequence || open !== props.modelValue || id !== props.instanceId) return
    activeId.value = id
    visible.value = open
  },
  { immediate: true }
)
defineExpose({ requestLeave })
</script>
