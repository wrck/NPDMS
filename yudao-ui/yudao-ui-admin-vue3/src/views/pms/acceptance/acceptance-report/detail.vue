<template>
  <Dialog :model-value="visible" :before-close="beforeClose" :title="activity ? `查看${typeLabel(activity.acceptanceType)}报告` : '查看验收报告'" width="min(640px, 95vw)" @update:model-value="onVisibilityChange">
    <el-skeleton v-if="loading" :rows="6" animated />
    <el-alert v-else-if="errorText" :title="errorText" type="error" :closable="false" />
    <AcceptanceReportForm v-else-if="activity" readonly :operator-name="recordedOperator" :completion-time="current?.acceptanceTime" :reports="fileReports" />
    <template #footer>
      <div class="detail-actions">
        <el-button :disabled="isDirty()" @click="close">关闭</el-button>
        <el-button v-if="versions.length" v-hasPermi="['pms:acceptance:report:query']" @click="openHistory">查看版本历史</el-button>
        <el-button v-if="canWrite('UPDATE')" type="primary" v-hasPermi="['pms:acceptance:report:write']" @click="openEditor">{{ draft ? '继续上传并保存' : '编辑' }}</el-button>
        <el-button v-if="current && canWrite('REVOKE')" type="danger" plain v-hasPermi="['pms:acceptance:report:write']" @click="revoke">撤销当前版本</el-button>
      </div>
    </template>
  </Dialog>
  <ReportDraftEditor ref="editorRef" :readonly="readonly || !activityAllowsReportWrite(activity)" :allowed-actions="allowedActions" @changed="reload" @dirty-change="emit('dirty-change', $event)" />
  <ReportVersionHistoryDrawer ref="historyRef" :allowed-actions="allowedActions" />
</template>
<script setup lang="ts">
import { generateUUID } from '@/utils'
import { activityAllowsReportWrite } from './reportState'
import { useMessage } from '@/hooks/web/useMessage'
import { checkPermi } from '@/utils/permission'
import { isBusinessViewId, legacyOwnerId, sameBusinessViewId, type BusinessViewId } from '@/api/pms/platform/business-view/ids'
import * as ReportApi from '@/api/pms/acceptance/acceptance-report'
import type { AcceptanceActivityVO, AcceptanceReportVersionVO } from '@/api/pms/acceptance/acceptance-report'
import { useOperationClient } from '@/components/BusinessView/operationHost'
import type { OperationClient } from '@/components/BusinessView/operationClient'
import ReportDraftEditor from './ReportDraftEditor.vue'
import ReportVersionHistoryDrawer from './ReportVersionHistoryDrawer.vue'
import AcceptanceReportForm from './AcceptanceReportForm.vue'
const props = defineProps<{ readonly?: boolean; allowedActions?: string[] }>()
const emit = defineEmits<{ changed: []; 'dirty-change': [value: boolean] }>()
const executionClient = useOperationClient()
let openedClient: OperationClient | undefined
const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const errorText = ref('')
const activity = ref<AcceptanceActivityVO>()
const versions = ref<AcceptanceReportVersionVO[]>([])
const editorRef = ref<InstanceType<typeof ReportDraftEditor>>()
const historyRef = ref<InstanceType<typeof ReportVersionHistoryDrawer>>()
const revokeKey = ref(generateUUID())
const current = computed(() => versions.value.find(item => item.reportStatus === 'EFFECTIVE'))
const draft = computed(() => versions.value.find(item => item.reportStatus === 'DRAFT'))
const fileReports = computed(() => [current.value, draft.value].filter((report): report is AcceptanceReportVersionVO => !!report))
const recordedOperator = computed(() => {
  const report = current.value ?? draft.value
  return report?.acceptorName || (report?.uploaderUserId == null ? undefined : String(report.uploaderUserId))
})
let sequence = 0
let openSequence = 0
let expectedProject: BusinessViewId | undefined
const revoking = ref(false)
const canQuery = () => (props.allowedActions === undefined || props.allowedActions.includes('QUERY')) && checkPermi(['pms:acceptance:report:query'])
const canWrite = (action: string) => !props.readonly && canQuery() && activityAllowsReportWrite(activity.value)
  && (props.allowedActions === undefined || props.allowedActions.includes(action)) && checkPermi(['pms:acceptance:report:write'])
const isDirty = () => revoking.value || !!editorRef.value?.isDirty()
const requestLeave = async () => !revoking.value && (await editorRef.value?.requestLeave() ?? true)
const discardChanges = () => {
  if (revoking.value || editorRef.value?.discardChanges() === false) return false
  sequence++; openSequence++; visible.value = false; loading.value = false
  activity.value = undefined; versions.value = []; openedClient = undefined
  historyRef.value?.close()
  return true
}
const beforeClose = async (done: () => void) => { if (await requestLeave()) { if (discardChanges()) done() } }
const close = async () => { if (await requestLeave()) discardChanges() }
const onVisibilityChange = (value: boolean) => { if (!value && visible.value) void close() }
const open = async (id: BusinessViewId, projectId?: BusinessViewId, upload = false) => {
  const opening = ++openSequence
  if (!isBusinessViewId(id) || (projectId !== undefined && !isBusinessViewId(projectId)) || !canQuery()) return false
  if (visible.value && sameBusinessViewId(activity.value?.id, id) && (projectId === undefined || sameBusinessViewId(activity.value?.projectId, projectId))) return true
  if (!(await requestLeave()) || opening !== openSequence || !discardChanges()) return false
  expectedProject = projectId
  openedClient = executionClient.value
  const loaded = await load(id, !upload)
  if (loaded && upload && activity.value && canWrite('UPDATE')) return await editorRef.value?.open(activity.value, draft.value)
  return loaded
}
const load = async (id: BusinessViewId, show = false) => {
  const token = ++sequence, project = expectedProject
  errorText.value = ''; loading.value = true
  try {
    const target = await ReportApi.getActivity(legacyOwnerId(id))
    if (token !== sequence || !canQuery()) return false
    if (!sameBusinessViewId(target.id, id) || (project !== undefined && !sameBusinessViewId(target.projectId, project))) { message.warning('该验收活动不属于当前项目'); return false }
    const reports = await ReportApi.getReportVersions(legacyOwnerId(id))
    if (token !== sequence || !canQuery() || reports.some(report => !sameBusinessViewId(report.acceptanceId, id))) return false
    activity.value = target; versions.value = reports
    if (show) visible.value = true
    return true
  } catch {
    if (token === sequence) { errorText.value = '验收报告详情加载失败，请关闭后重试。'; if (show) visible.value = true }
    return false
  } finally { if (token === sequence) loading.value = false }
}
const reload = async () => { emit('changed'); if (activity.value && canQuery()) await load(activity.value.id) }
const openEditor = async () => {
  if (!visible.value || !activity.value || !canWrite('UPDATE') || revoking.value) return false
  const opened = await editorRef.value?.open(activity.value, draft.value)
  if (opened) visible.value = false
  return opened
}
const openHistory = () => { if (activity.value && canQuery()) void historyRef.value?.open(activity.value.id) }
const revoke = async () => {
  if (!visible.value || !activity.value || !current.value || !canWrite('REVOKE') || revoking.value || editorRef.value?.isDirty()) return false
  const token = sequence, target = activity.value, report = current.value
  try { await message.confirm('撤销后不会恢复旧版本，确认继续？') } catch { return false }
  if (token !== sequence || !canWrite('REVOKE') || revoking.value || editorRef.value?.isDirty()) return false
  revoking.value = true; emit('dirty-change', true)
  try {
    await ReportApi.revokeCurrentVersion(target, report, revokeKey.value, openedClient)
    if (token !== sequence) return false
    revokeKey.value = generateUUID(); message.success('当前报告版本已撤销'); await reload()
    return true
  } finally { revoking.value = false; emit('dirty-change', isDirty()) }
}
onBeforeUnmount(() => { sequence++; openSequence++ })
const typeLabel = (type: string) => (type === 'FINAL' ? '终验' : '初验')
defineExpose({ open, requestLeave, discardChanges, isDirty })
</script>
<style scoped lang="scss">
.detail-actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; justify-content: flex-end; }
@media (width <= 767px) { .detail-actions { align-items: stretch; flex-direction: column; } .detail-actions .el-button { width: 100%; margin-left: 0; } }
</style>
