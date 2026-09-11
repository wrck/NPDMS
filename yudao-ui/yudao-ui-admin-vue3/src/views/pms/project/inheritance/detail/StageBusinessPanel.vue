<template>
  <section v-loading="loading" aria-label="阶段业务办理">
    <el-alert v-if="error || context?.recoverableError" type="warning" :closable="false" :title="error || unavailableMessage">
      <el-button link @click="refresh">重新加载阶段绑定</el-button>
    </el-alert>
    <el-alert v-if="!error && !context?.recoverableError && context?.bindingType === 'STAGE_NATIVE'" type="info" :closable="false"
      title="本阶段使用原生办理；按阶段自身的完成与门禁规则判定，不挂载外部业务页面。" />
    <BusinessViewHost v-if="context?.businessView" ref="hostRef" :registration="context.businessView"
      :resolved-context="{ project }" :allowed-actions="error || loading ? [] : context.ownerActions" :readonly="!!error || loading || context.readonly"
      @changed="handleChanged" @dirty-change="emit('dirty-change', $event)" />
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import BusinessViewHost from '@/components/BusinessView/BusinessViewHost.vue'
import { getStageBusinessContext, type StageBusinessContext } from '@/api/pms/project/stage-business'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
const props = defineProps<{ project: ProjectMasterVO; stageCode: string }>()
const emit = defineEmits<{ changed: []; 'dirty-change': [boolean] }>()
const context = ref<StageBusinessContext>()
const hostRef = ref<InstanceType<typeof BusinessViewHost>>()
const loading = ref(false)
const error = ref('')
const unavailableMessage = computed(() => `阶段冻结绑定暂不可用（${context.value?.recoverableError}），未修改业务记录或阶段状态。`)
let sequence = 0
const load = async () => {
  if (!props.project.id) return
  const token = ++sequence
  loading.value = true
  error.value = ''
  try {
    const value = await getStageBusinessContext(props.project.id, props.stageCode)
    if (token !== sequence) return
    if (String(value.projectId) !== String(props.project.id) || value.stageCode !== props.stageCode)
      throw new Error('stage identity mismatch')
    context.value = context.value?.businessView && !value.businessView && hostRef.value?.isDirty?.()
      ? { ...context.value, ownerActions: [], readonly: true, recoverableError: value.recoverableError || 'VIEW_UNAVAILABLE' }
      : value
  } catch {
    if (token === sequence) error.value = '阶段业务上下文加载失败，请重试。'
  } finally {
    if (token === sequence) loading.value = false
  }
}
const requestLeave = async () => (await hostRef.value?.requestLeave()) !== false
const refresh = async () => { if (await requestLeave()) await load() }
const handleChanged = async () => { await load(); emit('changed') }
watch(() => [props.project.id, props.stageCode], () => { context.value = undefined; void load() }, { immediate: true })
onBeforeUnmount(() => { ++sequence })
defineExpose({ requestLeave, refresh })
</script>
