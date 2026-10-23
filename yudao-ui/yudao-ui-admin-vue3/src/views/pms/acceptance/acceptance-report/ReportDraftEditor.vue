<template>
  <Dialog :model-value="visible" :title="`上传${typeLabel(activity?.acceptanceType)}交付件`" width="min(640px, 95vw)" :before-close="beforeClose" @update:model-value="onVisibilityChange">
    <AcceptanceReportForm v-if="activity" :operator-name="operatorName" :reports="fileReports" :uploaded-count="uploadedCount">
      <template #uploader>
      <PmsFileUploader
        v-if="draftId"
        ref="uploaderRef"
        hide-submit
        accept=".pdf,.png,.jpg,.jpeg,.doc,.docx,.txt"
        :key="uploadSession"
        :disabled="!canUpload || saving || publishing"
        owner-context="ACC"
        object-type="ACCEPTANCE_REPORT_VERSION"
        :object-id="String(draftId)"
        purpose-code="ACCEPTANCE_REPORT_ATTACHMENT"
        :reference-key="referenceKey"
        category-code="ACCEPTANCE_REPORT_ATTACHMENT"
        @completed="attachmentCompleted"
      />
      </template>
    </AcceptanceReportForm>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button v-if="canUpdate && canPublish" type="primary" :disabled="busy || completing" :loading="saving || publishing || completing" @click="saveAndComplete">保存</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { generateUUID } from '@/utils'
import { useUserStore } from '@/store/modules/user'
import { activityAllowsReportWrite } from './reportState'
import { PmsFileUploader } from '@/components/PmsFileArtifact'
import { useMessage } from '@/hooks/web/useMessage'
import { checkPermi } from '@/utils/permission'
import * as ReportApi from '@/api/pms/acceptance/acceptance-report'
import type { AcceptanceActivityVO, AcceptanceReportVersionVO, ReportAttachmentVO } from '@/api/pms/acceptance/acceptance-report'
import AcceptanceReportForm from './AcceptanceReportForm.vue'
import { useOperationClient, useOperationCheck } from '@/components/BusinessView/operationHost'
import type { OperationClient } from '@/components/BusinessView/operationClient'

const props = defineProps<{ readonly?: boolean; allowedActions?: string[] }>()
const emit = defineEmits<{ changed: []; 'dirty-change': [value: boolean] }>()
const executionClient = useOperationClient()
const waitForCheck = useOperationCheck()
let openedClient: OperationClient | undefined
const message = useMessage()
const userStore = useUserStore()
const visible = ref(false)
const saving = ref(false)
const publishing = ref(false)
const completing = ref(false)
let changedDuringCompletion = false
const notifyChanged = () => { if (completing.value) changedDuringCompletion = true; else emit('changed') }
const activity = ref<AcceptanceActivityVO>()
const draftId = ref<number>()
const draftNo = ref<number>()
const uploadedCount = ref(0)
const retainedAttachments = ref<ReportAttachmentVO[]>([])
const fileReports = computed(() => draftId.value && draftNo.value ? [{
  id: draftId.value, reportVersionNo: draftNo.value, reportStatus: 'DRAFT' as const, attachments: retainedAttachments.value
}] : [])
const referenceKey = ref(generateUUID())
const publishKey = ref(generateUUID())
const form = reactive<ReportApi.DraftContent>({})
const operatorName = computed(() => userStore.getUser.nickname || String(userStore.getUser.id))
const uploaderRef = ref<{ isBusy: () => boolean; hasPendingFile: () => boolean; submit: () => Promise<void> }>()
const uploadSession = ref(0)
const baseline = ref('')
let generation = 0
let openSequence = 0
const snapshot = () => JSON.stringify([form.acceptanceTime || '', form.conclusionCode || '', form.conclusionText || '', form.acceptorName || ''])
const allowed = (action: string) => !props.readonly && activityAllowsReportWrite(activity.value)
  && (props.allowedActions === undefined || (props.allowedActions.includes('QUERY') && props.allowedActions.includes(action)))
  && checkPermi(['pms:acceptance:report:write'])
const canUpdate = computed(() => allowed('UPDATE'))
const canPublish = computed(() => allowed('PUBLISH'))
// Embedded uploads remain closed until the Owner supplies the precise file action.
const canUpload = computed(() => allowed('FILE_WRITE') && checkPermi(['pms:file:upload']))
const busy = computed(() => saving.value || publishing.value || !!uploaderRef.value?.isBusy())
const dirty = computed(() => visible.value && (snapshot() !== baseline.value || !!uploaderRef.value?.hasPendingFile()))
watch([dirty, busy, completing], () => emit('dirty-change', dirty.value || busy.value || completing.value), { flush: 'sync' })
const requestLeave = async (): Promise<boolean> => {
  if (busy.value || completing.value) return false
  if (!dirty.value) return true
  try { await message.confirm('草稿有未保存的修改，确认放弃并离开？'); return !busy.value } catch { return false }
}
const discardChanges = (): boolean => {
  if (busy.value || completing.value) return false
  generation++
  openSequence++
  visible.value = false
  activity.value = undefined
  draftId.value = undefined
  draftNo.value = undefined
  retainedAttachments.value = []
  openedClient = undefined
  Object.keys(form).forEach(key => delete form[key as keyof ReportApi.DraftContent])
  baseline.value = snapshot()
  uploadSession.value++
  return true
}
const close = async () => { if (await requestLeave()) discardChanges() }
const beforeClose = async (done: () => void) => { if (await requestLeave()) { if (discardChanges()) done() } }
const onVisibilityChange = (value: boolean) => { if (!value && visible.value) void close() }
const open = async (target: AcceptanceActivityVO, draft?: AcceptanceReportVersionVO) => {
  const token = ++openSequence
  if (visible.value && activity.value?.id === target.id) return true
  if (!(await requestLeave()) || token !== openSequence || !discardChanges()) return false
  activity.value = { ...target }
  // A retained editor must not silently adopt a different task/plan/round.
  openedClient = executionClient.value
  draftId.value = draft?.id
  draftNo.value = draft?.reportVersionNo
  uploadedCount.value = draft?.attachments.length || 0
  retainedAttachments.value = [...(draft?.attachments || [])]
  referenceKey.value = generateUUID()
  publishKey.value = generateUUID()
  Object.assign(form, {
    expectedReportVersionNo: draft?.reportVersionNo,
    acceptanceTime: draft?.acceptanceTime,
    conclusionCode: draft?.conclusionCode,
    conclusionText: draft?.conclusionText,
    acceptorName: draft?.acceptorName
  })
  baseline.value = snapshot()
  visible.value = true
  if (!draftId.value && canUpdate.value) await saveDraft()
  return true
}

const saveDraft = async () => {
  if (!visible.value || !activity.value || !allowed('UPDATE') || busy.value) return false
  const token = generation
  const savedSnapshot = snapshot()
  const content = { ...form, expectedReportVersionNo: draftNo.value }
  saving.value = true
  try {
    const result = draftId.value
      ? await ReportApi.updateDraft(activity.value.id, draftId.value, content, activity.value.version, openedClient)
      : await ReportApi.createDraft(activity.value.id, content, activity.value.version, openedClient)
    if (token !== generation) return false
    draftId.value = result.reportVersionId
    draftNo.value = result.reportVersionNo
    form.expectedReportVersionNo = result.reportVersionNo
    if (result.activityVersion != null) activity.value = { ...activity.value, version: result.activityVersion }
    publishKey.value = generateUUID()
    baseline.value = savedSnapshot
    notifyChanged()
    return true
  } finally {
    if (token === generation) saving.value = false
  }
}

const attachmentCompleted = () => {
  if (!visible.value) return
  uploadedCount.value += 1
  referenceKey.value = generateUUID()
  notifyChanged()
}

const publish = async () => {
  if (!visible.value || !activity.value || !draftId.value || !draftNo.value || !allowed('PUBLISH') || busy.value) return false
  if (dirty.value) { message.warning('请先保存草稿修改并处理待上传文件'); return false }
  const token = generation
  publishing.value = true
  try {
    await ReportApi.publishVersion(activity.value, { id: draftId.value, reportVersionNo: draftNo.value }, publishKey.value, openedClient)
    if (token !== generation) return false
    message.success(`${typeLabel(activity.value.acceptanceType)}已通过`)
    visible.value = false
    notifyChanged()
    return true
  } finally {
    if (token === generation) publishing.value = false
  }
}
const saveAndComplete = async () => {
  if (!canUpdate.value || !canPublish.value || busy.value || completing.value) return false
  completing.value = true
  const token = generation
  try {
    if (uploaderRef.value?.hasPendingFile()) {
      if (!canUpload.value) return false
      await uploaderRef.value.submit()
      if (uploaderRef.value.hasPendingFile()) return false
    }
    if (!uploadedCount.value) { message.warning('请先上传验收报告'); return false }
    if (!(await waitForCheck())) return false
    await nextTick()
    if (token !== generation) return false
    if (!(await saveDraft())) { message.warning('交付件已保留为草稿，请刷新后检查操作权限和执行条件再保存'); return false }
    // A background capability read is temporarily query-only. Wait for its result;
    // publish still checks fresh Owner actions and the server reauthorizes the command.
    if (!(await waitForCheck())) return false
    await nextTick()
    if (token !== generation) return false
    const completed = await publish()
    if (!completed) message.warning('交付件已保留为草稿，请刷新后检查操作权限和执行条件再保存')
    return completed
  } catch (error) {
    message.warning('本次保存未完成，已上传文件仍保留在草稿中，请核查错误后重试')
    throw error
  } finally {
    completing.value = false
    if (changedDuringCompletion) { changedDuringCompletion = false; if (token === generation) emit('changed') }
  }
}
onBeforeUnmount(() => { generation++ })

const typeLabel = (type?: string) => (type === 'FINAL' ? '终验' : '初验')
defineExpose({ open, requestLeave, discardChanges, isDirty: () => dirty.value || busy.value || completing.value })
</script>

<style scoped lang="scss">
@media (width <= 767px) {
  :deep(.el-dialog__footer) { display: grid; gap: 8px; }
  :deep(.el-dialog__footer .el-button) { width: 100%; margin-left: 0; }
}
</style>
