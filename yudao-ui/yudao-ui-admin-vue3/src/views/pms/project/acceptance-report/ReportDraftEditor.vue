<template>
  <Dialog v-model="visible" :title="draftId ? '编辑报告草稿' : '新建报告草稿'" width="720px">
    <el-alert
      v-if="activity"
      :title="`${typeLabel(activity.acceptanceType)} · 活动版本 ${activity.version}`"
      type="info"
      :closable="false"
      class="editor-alert"
    />
    <el-form ref="formRef" :model="form" label-position="top">
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item label="验收时间"><el-date-picker v-model="form.acceptanceTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" class="!w-full" /></el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="验收结论"><el-select v-model="form.conclusionCode" clearable class="!w-full"><el-option label="通过" value="PASS" /><el-option label="不通过" value="FAIL" /><el-option label="有条件通过" value="CONDITIONAL_PASS" /></el-select></el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="验收人"><el-input v-model="form.acceptorName" maxlength="128" /></el-form-item>
      <el-form-item label="结论说明"><el-input v-model="form.conclusionText" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item>
    </el-form>

    <section v-if="draftId" class="attachment-section" aria-labelledby="attachment-title">
      <div class="section-heading">
        <div><h3 id="attachment-title">报告附件</h3><p>可连续上传多个附件；发布时由服务端冻结完整有序集合。</p></div>
        <el-tag type="info">本次已上传 {{ uploadedCount }} 个</el-tag>
      </div>
      <PmsFileUploader
        owner-context="ACC"
        object-type="ACCEPTANCE_REPORT_VERSION"
        :object-id="String(draftId)"
        purpose-code="ACCEPTANCE_REPORT_ATTACHMENT"
        :reference-key="referenceKey"
        category-code="ACCEPTANCE_REPORT_ATTACHMENT"
        @completed="attachmentCompleted"
      />
    </section>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button :loading="saving" @click="saveDraft">{{ draftId ? '保存草稿' : '创建草稿' }}</el-button>
      <el-button v-if="draftId" type="primary" :loading="publishing" @click="publish">发布当前草稿</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { PmsFileUploader } from '@/components/PmsFileArtifact'
import { useMessage } from '@/hooks/web/useMessage'
import * as ReportApi from '@/api/pms/project/acceptance-report'
import type { AcceptanceActivityVO, AcceptanceReportVersionVO } from '@/api/pms/project/acceptance-report'

const emit = defineEmits<{ changed: [] }>()
const message = useMessage()
const visible = ref(false)
const saving = ref(false)
const publishing = ref(false)
const activity = ref<AcceptanceActivityVO>()
const draftId = ref<number>()
const draftNo = ref<number>()
const uploadedCount = ref(0)
const referenceKey = ref(crypto.randomUUID())
const publishKey = ref(crypto.randomUUID())
const form = reactive<ReportApi.DraftContent>({})

const open = (target: AcceptanceActivityVO, draft?: AcceptanceReportVersionVO) => {
  activity.value = { ...target }
  draftId.value = draft?.id
  draftNo.value = draft?.reportVersionNo
  uploadedCount.value = draft?.attachments.length || 0
  referenceKey.value = crypto.randomUUID()
  publishKey.value = crypto.randomUUID()
  Object.assign(form, {
    expectedReportVersionNo: draft?.reportVersionNo,
    acceptanceTime: draft?.acceptanceTime,
    conclusionCode: draft?.conclusionCode,
    conclusionText: draft?.conclusionText,
    acceptorName: draft?.acceptorName
  })
  visible.value = true
}

const saveDraft = async () => {
  if (!activity.value) return
  saving.value = true
  try {
    const result = draftId.value
      ? await ReportApi.updateDraft(activity.value.id, draftId.value, { ...form, expectedReportVersionNo: draftNo.value }, activity.value.version)
      : await ReportApi.createDraft(activity.value.id, form, activity.value.version)
    draftId.value = result.reportVersionId
    draftNo.value = result.reportVersionNo
    form.expectedReportVersionNo = result.reportVersionNo
    message.success('草稿已保存')
    emit('changed')
  } finally {
    saving.value = false
  }
}

const attachmentCompleted = () => {
  uploadedCount.value += 1
  referenceKey.value = crypto.randomUUID()
}

const publish = async () => {
  if (!activity.value || !draftId.value || !draftNo.value) return
  publishing.value = true
  try {
    await ReportApi.publishVersion(activity.value, { id: draftId.value, reportVersionNo: draftNo.value }, publishKey.value)
    message.success(activity.value.currentReportVersionId ? '新版本已替换生效' : '报告版本已生效')
    visible.value = false
    emit('changed')
  } finally {
    publishing.value = false
  }
}

const typeLabel = (type?: string) => (type === 'FINAL' ? '终验' : '初验')
defineExpose({ open })
</script>

<style scoped lang="scss">
.editor-alert { margin-bottom: 16px; }
.attachment-section { padding-top: 16px; border-top: 1px solid var(--el-border-color-lighter); }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.section-heading h3 { margin: 0; font-size: 16px; color: var(--el-text-color-primary); }
.section-heading p { margin: 4px 0 0; font-size: 13px; color: var(--el-text-color-secondary); }
@media (width <= 767px) {
  .section-heading { flex-direction: column; }
  :deep(.el-dialog__footer) { display: grid; gap: 8px; }
  :deep(.el-dialog__footer .el-button) { width: 100%; margin-left: 0; }
}
</style>
