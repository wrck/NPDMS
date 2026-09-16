<template>
  <section class="task-business" aria-label="任务业务办理">
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <template v-if="context">
      <el-alert
        v-if="context.recoverableError"
        :title="context.recoverableError"
        type="warning"
        :closable="false"
      />
      <BusinessViewHost
        v-if="registration && project"
        ref="hostRef"
        :registration="registration"
        :resolved-context="{ project, taskId: context.taskId, taskExecution: context.execution, businessObjectId: selectedObject }"
        :allowed-actions="ownerActions"
        :readonly="viewReadonly"
        @changed="ownerChanged"
        @dirty-change="dirty = $event"
        @switch-blocked="restoreTarget"
      />
      <el-alert
        v-else-if="!loading && !context.businessViewRevisionId"
        title="当前任务尚未冻结可用的业务页面，请完善模板绑定；不会降级为通用完成按钮。"
        type="info"
        :closable="false"
      />
      <section v-if="artifacts.length" class="artifact-sources" aria-label="来源交付件">
        <h4>来源交付件</h4>
        <p>以下引用来自关联业务的有效版本，文件下载和归档仍由原业务权限控制。</p>
        <ul
          ><li
            v-for="file in artifacts"
            :key="`${file.artifactId}:${file.versionNo}:${file.referenceKey}`"
            >{{ file.displayName }} · 文件版本 {{ file.versionNo }} · 来源版本
            {{ file.sourceVersion }}</li
          ></ul
        >
      </section>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import BusinessViewHost from '@/components/BusinessView/BusinessViewHost.vue'
import * as BusinessApi from '@/api/pms/project/task-business'
import type { TaskBusinessContext } from '@/api/pms/project/task-business'
import {
  type BusinessViewId,
  type BusinessViewRegistrationVO
} from '@/api/pms/platform/business-view'
import { getProject, type ProjectMasterVO } from '@/api/pms/project/projects'
import { legacyOwnerId } from '@/api/pms/platform/business-view/ids'
const props = defineProps<{
  taskId: BusinessViewId
  taskVersion: number
  readonly?: boolean
  initialProject?: ProjectMasterVO
}>()
const emit = defineEmits<{
  changed: []
  'fact-version': [string | undefined]
  'dirty-change': [boolean]
}>()
const context = ref<TaskBusinessContext>()
const project = ref<Omit<ProjectMasterVO, 'id'> & { id?: BusinessViewId }>()
const registration = ref<BusinessViewRegistrationVO>()
const hostRef = ref<InstanceType<typeof BusinessViewHost>>()
const loading = ref(false), dirty = ref(false)
const selectedObject = ref<string>()
const error = ref('')
let sequence = 0
let generation = 0
let disposed = false
let pendingFactVersion: string | undefined
const clearFactVersion = () => {
  pendingFactVersion = undefined
  emit('fact-version', undefined)
}
const sameTask = (a: BusinessViewId | undefined, b: BusinessViewId) => String(a) === String(b)
const currentTask = (epoch: number, taskId: BusinessViewId) =>
  !disposed && epoch === generation && sameTask(taskId, props.taskId)
const viewReadonly = computed(() =>
  Boolean(props.readonly || loading.value || error.value || context.value?.recoverableError ||
    context.value?.executionAllowed === false ||
    !sameTask(context.value?.taskId, props.taskId))
)
// Owner pages enforce each row's state. Automatic links can lag behind a saved draft.
const ownerActions = computed(() => {
  if (viewReadonly.value) return []
  const actions = context.value?.ownerActions || []
  const record = context.value?.links.find((row) => row.objectId === selectedObject.value)
  if (!actions.includes('QUERY')) return []
  return [...new Set([
    ...actions.filter((action) => !['LINK', 'UNLINK'].includes(action)),
    ...(record?.allowedActions || []).filter((action) => !['LINK', 'UNLINK', 'CREATE'].includes(action))
  ])]
})
const artifacts = computed(() => context.value?.links.flatMap((row) => row.artifacts) || [])
const snapshotTarget = () => ({
  context: context.value, project: project.value, registration: registration.value,
  objectId: selectedObject.value
})
let previousTarget: ReturnType<typeof snapshotTarget> | undefined
const restoreTarget = () => {
  if (!previousTarget) return
  context.value = previousTarget.context
  project.value = previousTarget.project
  registration.value = previousTarget.registration
  selectedObject.value = previousTarget.objectId
  previousTarget = undefined
  error.value = '业务页面切换未完成，请先处理当前未保存的内容后重试。'
  clearFactVersion()
}
const load = async (useInitialProject = false) => {
  const current = ++sequence, epoch = generation, taskId = props.taskId
  const isCurrent = () => current === sequence && currentTask(epoch, taskId)
  loading.value = true
  error.value = ''
  clearFactVersion()
  try {
    const next = await BusinessApi.getTaskBusinessContext(taskId)
    if (!isCurrent()) return
    if (!sameTask(next.taskId, taskId)) throw new Error('Task context mismatch')
    // Reuse the enclosing project's authorized response only on entry and only
    // for the same project. Refreshes still query current Owner project facts.
    const initialProject = useInitialProject && props.initialProject?.id != null &&
      String(props.initialProject.id) === String(next.projectId) ? props.initialProject : undefined
    const detail = ['SOL_SITE_SURVEY', 'ACC_ACCEPTANCE_REPORT'].includes(next.businessView?.componentKey || '')
      ? { id: next.projectId }
      : next.businessView ? initialProject || await getProject(legacyOwnerId(next.projectId)) : undefined
    if (!isCurrent()) return
    if (hostRef.value?.isDirty() && (!next.businessView || !detail)) {
      throw new Error('Cannot unmount a dirty Owner page')
    }
    if (!dirty.value || !previousTarget) previousTarget = snapshotTarget()
    const switchingDirtyTarget = dirty.value && (
      !sameTask(context.value?.taskId, next.taskId) ||
      registration.value?.id !== next.businessView?.id ||
      !next.links.some((row) => row.objectId === selectedObject.value)
    )
    context.value = next
    project.value = detail
    registration.value = next.businessView
    if (!next.links.some((row) => row.objectId === selectedObject.value))
      selectedObject.value = next.links[0]?.objectId
    pendingFactVersion = !next.recoverableError && switchingDirtyTarget ? next.factVersion : undefined
    emit('fact-version', next.recoverableError || switchingDirtyTarget ? undefined : next.factVersion)
    // Outbox may complete the task without changing its already-confirmed Owner facts.
    // Refresh the enclosing workbench from the task version, not only the business fact version.
    if (next.execution && next.execution.taskVersion !== props.taskVersion) emit('changed')
  } catch {
    if (isCurrent()) {
      error.value = '无法读取业务关联或 Owner 项目上下文（可能缺少项目查询权限），请重试；原业务数据未改变。'
      clearFactVersion()
    }
  } finally {
    if (isCurrent()) loading.value = false
  }
}
const requestLeave = async () => {
  try {
    const host = hostRef.value
    if (!host) return !dirty.value
    return (await host.requestLeave()) !== false
  } catch {
    return false
  }
}
const refresh = async () => {
  const epoch = generation, taskId = props.taskId
  if (await requestLeave() && currentTask(epoch, taskId)) await load()
}
const ownerChanged = () => {
  // A retained old Host may finish an Owner request after the parent selected another task.
  if (sameTask(context.value?.taskId, props.taskId)) void load()
}
watch(
  () => props.taskId,
  async () => {
    const epoch = ++generation, taskId = props.taskId
    ++sequence
    loading.value = false
      clearFactVersion()
    if (!await requestLeave() || !currentTask(epoch, taskId)) return
    // Do not clear context/dirty here. Host performs the second-phase discard only
    // after the new target is available and its own pending switch is still current.
    await load(true)
  },
  { immediate: true, flush: 'sync' }
)
watch(() => [props.readonly, props.taskVersion], () => {
  clearFactVersion()
  if (sameTask(context.value?.taskId, props.taskId)) void load()
}, { flush: 'sync' })
watch(dirty, (value) => {
  if (value) previousTarget = snapshotTarget()
  else {
    previousTarget = undefined
    if (pendingFactVersion && !loading.value && sameTask(context.value?.taskId, props.taskId) &&
      !error.value && !context.value?.recoverableError) emit('fact-version', pendingFactVersion)
    pendingFactVersion = undefined
  }
  emit('dirty-change', value)
}, { flush: 'sync' })
onBeforeUnmount(() => {
  disposed = true
  ++sequence
  ++generation
})
defineExpose({ requestLeave, isDirty: () => dirty.value, refresh, loading })
</script>
<style scoped>
.task-business {
  min-width: 0;
}
h4 {
  margin: 0 0 8px;
}
p {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.artifact-sources {
  margin-top: 20px;
}
</style>
