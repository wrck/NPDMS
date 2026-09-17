<template>
  <section class="business-view-host" aria-label="业务视图">
    <section v-if="operation.requiresReopen.value || operation.mode.value !== 'INDEPENDENT' && operation.mode.value !== 'LEGACY'" aria-label="项目执行规则">
      <el-alert v-if="operation.failure.value || operation.observation.value?.reason"
        :title="operation.failure.value || operation.observation.value?.reason || ''" type="warning" :closable="false" />
      <el-alert v-if="operation.receipt.value"
        :title="`业务已提交：${operation.receipt.value.resultCode}；节点当前状态：${operation.observation.value?.node.status || '待刷新'}。业务提交不等于节点完成。`"
        type="info" :closable="false" />
      <el-alert v-if="operation.uncertain.value" title="上次操作响应未确认，请先用原请求确认结果；不要重新填写并重复提交。" type="warning" :closable="false" />
      <el-button v-if="operation.uncertain.value" :loading="operation.recovering.value" @click="operation.recover()">用原请求确认提交结果</el-button>
      <el-button v-if="operation.requiresReopen.value" @click="retry">处理未保存内容并重新进入</el-button>
      <el-button :loading="operation.checking.value" @click="operation.refresh()">刷新执行状态（不重载业务表单）</el-button>
      <el-table v-if="operation.observation.value?.actions.length" :data="operation.observation.value.actions" size="small" aria-label="操作权限与规则">
        <el-table-column prop="label" label="业务操作" min-width="120" />
        <el-table-column label="业务权限" width="120"><template #default="{ row }">{{ row.ownerPermitted ? '允许' : active.resolvedContext.businessObjectId == null ? '选择对象后校验' : '未获授权' }}</template></el-table-column>
        <el-table-column label="项目执行" width="100"><template #default="{ row }">{{ row.executionPermitted ? '允许' : '不可办理' }}</template></el-table-column>
        <el-table-column prop="pre.outcome" label="前置判定" width="160" />
        <el-table-column prop="reason" label="阻塞原因" min-width="180" />
      </el-table>
    </section>
    <el-alert v-if="loadError || resolved.error" :title="loadError || resolved.error" type="error" :closable="false" show-icon>
      <template #default><el-button link type="primary" @click="retry">重新装载</el-button></template>
    </el-alert>
    <component :is="resolved.component" v-else-if="operation.mode.value !== 'CHECKING'" :key="`${activeKey}:${retryNo}`" ref="contentRef" v-bind="resolved.props"
      @changed="ownerChanged" @dirty-change="setDirty" />
  </section>
</template>
<script setup lang="ts">
import { computed, onErrorCaptured, provide, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { resolveBusinessView, type BusinessViewTarget } from './registry'
import { editingTargetKey, operationClientKey, useOperationHost } from './operationHost'

defineOptions({ name: 'BusinessViewHost' })
const props = defineProps<BusinessViewTarget>()
const emit = defineEmits<{ changed: []; 'dirty-change': [value: boolean]; 'switch-blocked': [] }>()
const message = useMessage()
const capture = (): BusinessViewTarget => ({ registration: { ...props.registration }, resolvedContext: { ...props.resolvedContext },
  allowedActions: [...(props.allowedActions || [])], readonly: props.readonly })
const active = shallowRef(capture())
const operation = useOperationHost(active, () => emit('changed'))
provide(operationClientKey, operation.client)
const activeKey = computed(() => editingTargetKey(active.value))
const resolved = computed(() => resolveBusinessView({ ...active.value, resolvedContext: operation.decorated.value,
  allowedActions: operation.allowedActions.value, readonly: active.value.readonly || operation.requiresReopen.value }))
const contentRef = ref<{ requestLeave?: () => Promise<boolean>; discardChanges?: () => boolean; isDirty?: () => boolean }>()
const dirty = ref(false)
const loadError = ref('')
const retryNo = ref(0)
const setDirty = (value: boolean) => { dirty.value = value; emit('dirty-change', value) }
const ownerChanged = () => { emit('changed'); void operation.refresh(true) }
let leaving: Promise<boolean> | undefined
const requestLeave = (): Promise<boolean> => {
  if (operation.client.value?.isBusy()) return Promise.resolve(false)
  if (operation.client.value?.hasUncertain()) {
    message.warning('上次操作响应未确认，请先用原请求确认提交结果，再切换或重新进入。')
    return Promise.resolve(false)
  }
  if (leaving) return leaving
  leaving = (async () => {
    if (contentRef.value?.requestLeave) return await contentRef.value.requestLeave()
    if (!dirty.value) return true
    message.warning('当前视图尚未保存且无法安全关闭，请先处理当前修改。')
    return false
  })().finally(() => { leaving = undefined })
  return leaving
}
let switchSequence = 0
watch(() => [editingTargetKey(props), props.registration, props.resolvedContext, props.allowedActions, props.readonly], async () => {
  const sequence = ++switchSequence, next = capture()
  if (editingTargetKey(next) !== activeKey.value) {
    if (!(await requestLeave())) { if (sequence === switchSequence) emit('switch-blocked'); return }
    if (sequence !== switchSequence || editingTargetKey(next) !== editingTargetKey(props)) return
    if (contentRef.value?.discardChanges?.() === false) { emit('switch-blocked'); return }
    loadError.value = ''; setDirty(false)
  }
  active.value = next
}, { deep: true })
const retry = async () => {
  const sequence = ++switchSequence
  if (!(await requestLeave()) || sequence !== switchSequence || contentRef.value?.discardChanges?.() === false) return
  loadError.value = ''; retryNo.value++
  await operation.reopen()
}
onErrorCaptured(() => { loadError.value = '业务组件暂不可用；请先确认已提交操作的结果，再重试装载。'; return false })
onBeforeRouteLeave(requestLeave)
defineExpose({ requestLeave, isDirty: () => dirty.value || !!operation.client.value?.isBusy() || !!operation.client.value?.hasUncertain() })
</script>
<style scoped>
.business-view-host { min-width: 0; }
</style>
