<template>
  <section v-loading="loading" aria-label="阶段业务办理">
    <el-alert v-if="error || context?.recoverableError" type="warning" :closable="false" :title="error || unavailableMessage">
      <el-button link @click="refresh">重新加载阶段绑定</el-button>
    </el-alert>
    <el-alert v-if="!error && !context?.recoverableError && context?.bindingType === 'STAGE_NATIVE'" type="info" :closable="false"
      title="本阶段使用原生办理；按阶段自身的完成与门禁规则判定，不挂载外部业务页面。" />
    <BusinessViewHost v-if="context?.businessView" ref="hostRef" :registration="context.businessView"
      :resolved-context="{ project, stageExecution: context.execution, stageCode: context.stageCode }" :allowed-actions="error || loading ? [] : context.ownerActions" :readonly="!!error || loading || context.readonly"
      @changed="handleChanged" @dirty-change="emit('dirty-change', $event)" />
    <StageApprovalPanel v-if="context?.bindingType === 'APPROVAL'" ref="approvalRef" :workbench="context"
      :disabled="!!error || loading" @changed="handleChanged" />
    <el-card v-if="context?.bindingType === 'PAGE'" shadow="never" class="stage-page-card">
      <template #header>页面办理</template>
      <p class="stage-page-hint">本阶段通过专用页面办理业务；路由为模板冻结的入口，仅用于跳转，不产生阶段完成事实。</p>
      <el-button type="primary" :disabled="!!error || loading || !context.routePath" @click="openPage">打开页面</el-button>
      <span v-if="context.routePath" class="stage-page-route">{{ context.routePath }}</span>
    </el-card>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import BusinessViewHost from '@/components/BusinessView/BusinessViewHost.vue'
import StageApprovalPanel from './StageApprovalPanel.vue'
import { getStageBusinessContext, type StageBusinessContext } from '@/api/pms/project/stage-business'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
const props = defineProps<{ project: ProjectMasterVO; stageCode: string }>()
const emit = defineEmits<{ changed: []; 'dirty-change': [boolean] }>()
const context = ref<StageBusinessContext>()
const hostRef = ref<InstanceType<typeof BusinessViewHost>>()
const approvalRef = ref<InstanceType<typeof StageApprovalPanel>>()
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
const requestLeave = async () => (await hostRef.value?.requestLeave()) !== false && (await approvalRef.value?.requestLeave()) !== false
const refresh = async () => { if (await requestLeave()) await load() }
const handleChanged = async () => { await load(); emit('changed') }
const router = useRouter()
const openPage = () => {
  if (context.value?.routePath)
    void router.push(context.value.routePath.replaceAll('{projectId}', encodeURIComponent(String(props.project.id))))
}
watch([() => props.project.id, () => props.stageCode], () => { context.value = undefined; void load() }, { immediate: true })
onBeforeUnmount(() => { ++sequence })
defineExpose({ requestLeave, refresh, isBusy: () => loading.value || !!approvalRef.value?.isBusy() })
</script>
<style scoped>
.stage-page-hint { margin: 0 0 12px; color: var(--el-text-color-secondary); }
.stage-page-route { margin-left: 12px; color: var(--el-text-color-secondary); font-family: monospace; }
</style>
