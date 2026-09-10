<template>
  <section class="task-business" aria-label="任务业务办理">
    <div class="business-heading"
      ><div><h3>业务办理</h3><p>关联已有业务记录，继续使用原模块页面办理。</p></div
      ><el-button :loading="loading" @click="refresh">刷新业务结果</el-button></div
    >
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <template v-if="context">
      <el-alert
        v-if="context.recoverableError"
        :title="context.recoverableError"
        type="warning"
        :closable="false"
      />
      <div v-if="canAct('LINK')" class="link-controls">
        <el-select
          v-model="selectedCandidate"
          placeholder="选择本项目已有业务记录"
          filterable
          :loading="candidateLoading"
          @visible-change="loadCandidates"
        >
          <el-option
            v-for="candidate in candidates"
            :key="candidate.objectId"
            :value="candidate.objectId"
            :label="candidate.displayName"
          />
        </el-select>
        <el-button :disabled="!selectedCandidate || saving" @click="link">关联记录</el-button>
      </div>
      <el-empty
        v-if="!context.links.length"
        description="尚未关联业务记录。页面保存不会自动完成任务。"
        :image-size="60"
      />
      <ul v-else class="business-records">
        <li v-for="record in context.links" :key="String(record.id)">
          <el-button link type="primary" @click="selectRecord(record.objectId)">{{
            record.displayName
          }}</el-button>
          <span class="fact-summary">{{ factSummary(record) }}</span>
          <el-button
            v-if="
              canAct('UNLINK') && record.allowedActions.includes('UNLINK')
            "
            link
            type="danger"
            :disabled="saving"
            @click="unlink(record)"
            >解除关联</el-button
          >
        </li>
      </ul>
      <BusinessViewHost
        v-if="registration && project"
        ref="hostRef"
        :registration="registration"
        :resolved-context="{ project, taskId: context.taskId, businessObjectId: selectedObject }"
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
import type {
  TaskBusinessContext,
  TaskBusinessLink,
  TaskBusinessObject
} from '@/api/pms/project/task-business'
import {
  type BusinessViewId,
  type BusinessViewRegistrationVO
} from '@/api/pms/platform/business-view'
import { getProject, type ProjectMasterVO } from '@/api/pms/project/projects'
import { legacyOwnerId } from '@/api/pms/platform/business-view/ids'
import { useMessage } from '@/hooks/web/useMessage'
const props = defineProps<{ taskId: BusinessViewId; taskVersion: number; readonly?: boolean }>()
const emit = defineEmits<{
  changed: []
  'fact-version': [string | undefined]
  'dirty-change': [boolean]
}>()
const message = useMessage()
const context = ref<TaskBusinessContext>()
const project = ref<Omit<ProjectMasterVO, 'id'> & { id?: BusinessViewId }>()
const registration = ref<BusinessViewRegistrationVO>()
const hostRef = ref<InstanceType<typeof BusinessViewHost>>()
const loading = ref(false),
  saving = ref(false),
  candidateLoading = ref(false),
  dirty = ref(false)
const candidates = ref<TaskBusinessObject[]>([])
const selectedCandidate = ref<string>(),
  selectedObject = ref<string>()
const error = ref('')
let sequence = 0
let generation = 0
let candidateSequence = 0
let selectionSequence = 0
let disposed = false
let pendingFactVersion: string | undefined
const clearFactVersion = () => {
  pendingFactVersion = undefined
  emit('fact-version', undefined)
}
const keys = new Map<string, string>()
const key = (intent: string) => {
  if (!keys.has(intent)) keys.set(intent, crypto.randomUUID())
  return keys.get(intent)!
}
const sameTask = (a: BusinessViewId | undefined, b: BusinessViewId) => String(a) === String(b)
const currentTask = (epoch: number, taskId: BusinessViewId) =>
  !disposed && epoch === generation && sameTask(taskId, props.taskId)
const viewReadonly = computed(() =>
  Boolean(props.readonly || loading.value || error.value || context.value?.recoverableError ||
    !sameTask(context.value?.taskId, props.taskId))
)
const canAct = (action: string) =>
  !viewReadonly.value && context.value?.allowedActions.includes(action) === true
// Owner permissions are authoritative; record-level actions cannot grant a missing Owner action.
const ownerActions = computed(() => {
  if (viewReadonly.value) return []
  const actions = context.value?.ownerActions || []
  const record = context.value?.links.find((row) => row.objectId === selectedObject.value)
  if (!actions.includes('QUERY')) return []
  return [...new Set([
    ...actions.filter((action) => action === 'QUERY' || action === 'CREATE'),
    ...(record?.allowedActions || []).filter((action) => !['LINK', 'UNLINK', 'CREATE'].includes(action))
  ])]
})
const artifacts = computed(() => context.value?.links.flatMap((row) => row.artifacts) || [])
const factSummary = (record: TaskBusinessLink) =>
  Object.entries(record.completionFacts)
    .map(([name, result]) => `${name}：${result ? '已满足' : '未满足'}`)
    .join('；') || '尚无完成事实'
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
const invalidateCandidates = () => {
  ++candidateSequence
  candidates.value = []
  selectedCandidate.value = undefined
  candidateLoading.value = false
}
const load = async () => {
  const current = ++sequence, epoch = generation, taskId = props.taskId
  const isCurrent = () => current === sequence && currentTask(epoch, taskId)
  loading.value = true
  error.value = ''
  clearFactVersion()
  invalidateCandidates()
  try {
    const next = await BusinessApi.getTaskBusinessContext(taskId)
    if (!isCurrent()) return
    if (!sameTask(next.taskId, taskId)) throw new Error('Task context mismatch')
    // The survey adapter only consumes an authorized project ID. Other Owner pages still
    // require their full project response; a denied getProject must never become a fake grant.
    const detail = ['SOL_SITE_SURVEY', 'ACC_ACCEPTANCE_REPORT'].includes(next.businessView?.componentKey || '')
      ? { id: next.projectId }
      : next.businessView ? await getProject(legacyOwnerId(next.projectId)) : undefined
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
  if (saving.value) return false
  try {
    const host = hostRef.value
    if (!host) return !dirty.value
    return (await host.requestLeave()) !== false && !saving.value
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
const selectRecord = (objectId: string) => {
  if (saving.value || loading.value || !sameTask(context.value?.taskId, props.taskId) ||
    !context.value?.links.some((row) => row.objectId === objectId)) return
  // Keep the Host mounted: its own target watcher confirms, then discards, exactly once.
  if (!dirty.value || !previousTarget) previousTarget = snapshotTarget()
  selectedObject.value = objectId
}
const loadCandidates = async (opened: boolean) => {
  if (!opened || !canAct('LINK')) return
  const taskId = props.taskId, epoch = generation, current = ++candidateSequence
  const isCurrent = () => current === candidateSequence && currentTask(epoch, taskId)
  candidateLoading.value = true
  try {
    const rows = await BusinessApi.getTaskBusinessCandidates(taskId)
    if (isCurrent() && canAct('LINK')) candidates.value = rows
  } catch {
    if (isCurrent()) {
      candidates.value = []
      selectedCandidate.value = undefined
      message.warning('无法读取可关联记录，请重试。')
    }
  } finally {
    if (isCurrent()) candidateLoading.value = false
  }
}
const link = async () => {
  if (!context.value || !selectedCandidate.value || saving.value || !canAct('LINK')) return
  const taskId = props.taskId, epoch = generation,
    objectId = selectedCandidate.value, contract = context.value.contractVersion,
    taskVersion = props.taskVersion
  const intent = `link:${taskId}:${taskVersion}:${contract}:${objectId}`
  saving.value = true
  clearFactVersion()
  try {
    await BusinessApi.linkTaskBusinessObject(taskId, objectId, taskVersion, contract, key(intent))
    keys.delete(intent)
    if (!currentTask(epoch, taskId)) return
    selectedCandidate.value = undefined
    await load()
    if (currentTask(epoch, taskId)) emit('changed')
  } catch {
    if (currentTask(epoch, taskId)) message.warning('关联未确认成功，可重试；不会自动完成任务。')
  } finally {
    saving.value = false
  }
}
const unlink = async (record: TaskBusinessLink) => {
  const allowed = () => canAct('UNLINK') && context.value?.links.some((row) =>
    String(row.id) === String(record.id) && row.objectId === record.objectId &&
    row.allowedActions.includes('UNLINK')) === true
  if (!context.value || saving.value || !allowed()) return
  const taskId = props.taskId, epoch = generation, taskVersion = props.taskVersion,
    contract = context.value.contractVersion, current = ++selectionSequence
  try {
    await message.confirm('仅解除任务与记录的关联，不删除业务记录和历史。是否继续？')
  } catch {
    return
  }
  if (!currentTask(epoch, taskId) || current !== selectionSequence || saving.value ||
    taskVersion !== props.taskVersion || contract !== context.value?.contractVersion || !allowed()) return
  const intent = `unlink:${taskId}:${taskVersion}:${contract}:${record.id}`
  saving.value = true
  clearFactVersion()
  try {
    await BusinessApi.unlinkTaskBusinessObject(taskId, record.id, taskVersion, contract, key(intent))
    keys.delete(intent)
    if (!currentTask(epoch, taskId)) return
    await load()
    if (currentTask(epoch, taskId)) emit('changed')
  } catch {
    if (currentTask(epoch, taskId)) message.warning('解除关联未确认成功，可重试；业务记录和历史未被删除。')
  } finally {
    saving.value = false
  }
}
watch(
  () => props.taskId,
  async () => {
    const epoch = ++generation, taskId = props.taskId
    ++sequence
    ++selectionSequence
    loading.value = false
    invalidateCandidates()
    clearFactVersion()
    if (!await requestLeave() || !currentTask(epoch, taskId)) return
    // Do not clear context/dirty here. Host performs the second-phase discard only
    // after the new target is available and its own pending switch is still current.
    await load()
  },
  { immediate: true, flush: 'sync' }
)
watch(() => [props.readonly, props.taskVersion], () => {
  ++selectionSequence
  invalidateCandidates()
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
  ++candidateSequence
})
defineExpose({ requestLeave, isDirty: () => dirty.value, refresh })
</script>
<style scoped>
.task-business {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color);
  min-width: 0;
}
.business-heading,
.link-controls,
.business-records li {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.business-heading {
  justify-content: space-between;
}
h3,
h4 {
  margin: 0 0 8px;
}
p,
.fact-summary {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.link-controls {
  margin: 16px 0;
}
.link-controls .el-select {
  width: 320px;
  max-width: 100%;
}
.business-records {
  padding: 0;
  list-style: none;
}
.business-records li {
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.artifact-sources {
  margin-top: 20px;
}
</style>
