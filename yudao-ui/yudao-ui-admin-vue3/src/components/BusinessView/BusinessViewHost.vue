<template>
  <section class="business-view-host" aria-label="业务视图">
    <el-alert
      v-if="loadError || resolved.error"
      :title="loadError || resolved.error"
      type="error"
      :closable="false"
      show-icon
    >
      <template #default
        ><el-button link type="primary" @click="retry">重新装载</el-button></template
      >
    </el-alert>
    <component
      :is="resolved.component"
      v-else
      :key="`${activeKey}:${retryNo}`"
      ref="contentRef"
      v-bind="resolved.props"
      @changed="emit('changed')"
      @dirty-change="setDirty"
    />
  </section>
</template>

<script setup lang="ts">
import { onBeforeRouteLeave } from 'vue-router'
import { businessViewTargetKey, resolveBusinessView, type BusinessViewTarget } from './registry'

// PM-03. Do not key this Host by node/id: callers must await requestLeave before unmounting it.
defineOptions({ name: 'BusinessViewHost' })
const props = defineProps<BusinessViewTarget>()
const emit = defineEmits<{ changed: []; 'dirty-change': [value: boolean]; 'switch-blocked': [] }>()
const message = useMessage()
const capture = (): BusinessViewTarget => ({
  registration: { ...props.registration },
  resolvedContext: { ...props.resolvedContext },
  allowedActions: [...(props.allowedActions || [])],
  readonly: props.readonly
})
const active = shallowRef(capture())
const activeKey = computed(() => businessViewTargetKey(active.value))
const resolved = computed(() => resolveBusinessView(active.value))
const contentRef = ref<{
  requestLeave?: () => Promise<boolean>
  discardChanges?: () => boolean
  isDirty?: () => boolean
}>()
const dirty = ref(false)
const loadError = ref('')
const retryNo = ref(0)
const setDirty = (value: boolean) => {
  dirty.value = value
  emit('dirty-change', value)
}
let leaving: Promise<boolean> | undefined
const requestLeave = (): Promise<boolean> => {
  if (leaving) return leaving
  leaving = (async () => {
    if (contentRef.value?.requestLeave) return await contentRef.value.requestLeave()
    if (!dirty.value) return true
    message.warning('当前视图尚未保存且无法安全关闭，请先处理当前修改。')
    return false
  })().finally(() => {
    leaving = undefined
  })
  return leaving
}
let switchSequence = 0
watch(
  () => [
    businessViewTargetKey(props),
    props.registration,
    props.resolvedContext,
    props.allowedActions,
    props.readonly
  ],
  async () => {
    const sequence = ++switchSequence
    const next = capture()
    if (businessViewTargetKey(next) !== activeKey.value) {
      if (!(await requestLeave())) {
        if (sequence === switchSequence) emit('switch-blocked')
        return
      }
      if (
        sequence !== switchSequence ||
        businessViewTargetKey(next) !== businessViewTargetKey(props)
      )
        return
      if (contentRef.value?.discardChanges?.() === false) {
        emit('switch-blocked')
        return
      }
      loadError.value = ''
      setDirty(false)
    }
    active.value = next
  },
  { deep: true }
)
const retry = async () => {
  const sequence = ++switchSequence
  if (!(await requestLeave()) || sequence !== switchSequence) return
  if (contentRef.value?.discardChanges?.() === false) return
  loadError.value = ''
  retryNo.value++
}
onErrorCaptured(() => {
  loadError.value = '业务组件暂不可用；未创建对象或改变业务状态，请重试。'
  return false
})
onBeforeRouteLeave(requestLeave)
defineExpose({ requestLeave, isDirty: () => dirty.value })
</script>

<style scoped>
.business-view-host {
  min-width: 0;
}
</style>
