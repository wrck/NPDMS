<template>
  <el-drawer v-model="visible" :title="activity ? `${typeLabel(activity.acceptanceType)}报告` : '验收报告'" :size="narrow ? '100%' : '720px'">
    <el-skeleton v-if="loading" :rows="6" animated />
    <template v-else-if="activity">
      <div class="detail-heading">
        <div><h2>{{ typeLabel(activity.acceptanceType) }}活动</h2><p>活动与报告版本独立；报告状态不会触发或反推验收范围绑定。</p></div>
        <el-tag :type="activity.activityStatus === 'COMPLETED' ? 'success' : 'warning'">{{ activityStatusLabel(activity.activityStatus) }}</el-tag>
      </div>

      <el-descriptions :column="narrow ? 1 : 2" border class="facts">
        <el-descriptions-item label="项目ID">{{ activity.projectId }}</el-descriptions-item>
        <el-descriptions-item label="任务ID">{{ activity.projectTaskId }}</el-descriptions-item>
        <el-descriptions-item label="活动版本">{{ activity.version }}</el-descriptions-item>
        <el-descriptions-item label="当前报告">{{ current ? `V${current.reportVersionNo}` : '尚无有效版本' }}</el-descriptions-item>
      </el-descriptions>

      <el-alert v-if="current?.archiveStatus === 'PENDING_COMPENSATION'" title="报告已生效，交付件归档待补偿；历史下载保持可用" type="warning" show-icon :closable="false" />
      <el-empty v-else-if="!current" description="当前尚无有效报告；可先保存不完整草稿，活动完成前再补齐四项与附件" />
      <section v-else class="current-report" aria-labelledby="current-report-title">
        <div class="section-title"><h3 id="current-report-title">当前有效报告 V{{ current.reportVersionNo }}</h3><el-tag type="success">EFFECTIVE</el-tag></div>
        <p>{{ current.conclusionText || '未填写结论说明' }}</p>
        <div class="current-meta"><span>验收人：{{ current.acceptorName }}</span><span>验收时间：{{ current.acceptanceTime }}</span><span>附件：{{ current.attachments.length }}</span></div>
      </section>

      <div class="detail-actions">
        <el-button v-if="activity.activityStatus === 'PENDING'" type="primary" v-hasPermi="['pms:acceptance:report:write']" @click="openEditor">{{ draft ? '继续编辑草稿' : current ? '创建替换版本' : '创建报告草稿' }}</el-button>
        <el-button v-if="versions.length" v-hasPermi="['pms:acceptance:report:query']" @click="historyRef?.open(activity.id)">查看版本历史</el-button>
        <el-button v-if="current && activity.activityStatus === 'PENDING'" type="danger" plain v-hasPermi="['pms:acceptance:report:write']" @click="revoke">撤销当前版本</el-button>
      </div>
      <p class="completion-note">对应项目任务完成时，服务端会再次校验当前有效报告的验收时间、结论、验收人和附件。</p>
    </template>
  </el-drawer>
  <ReportDraftEditor ref="editorRef" @changed="reload" />
  <ReportVersionHistoryDrawer ref="historyRef" />
</template>

<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import { useMessage } from '@/hooks/web/useMessage'
import * as ReportApi from '@/api/pms/project/acceptance-report'
import type { AcceptanceActivityVO, AcceptanceReportVersionVO } from '@/api/pms/project/acceptance-report'
import ReportDraftEditor from './ReportDraftEditor.vue'
import ReportVersionHistoryDrawer from './ReportVersionHistoryDrawer.vue'

const emit = defineEmits<{ changed: [] }>()
const message = useMessage()
const narrow = useMediaQuery('(width <= 767px)')
const visible = ref(false)
const loading = ref(false)
const activity = ref<AcceptanceActivityVO>()
const versions = ref<AcceptanceReportVersionVO[]>([])
const editorRef = ref<InstanceType<typeof ReportDraftEditor>>()
const historyRef = ref<InstanceType<typeof ReportVersionHistoryDrawer>>()
const revokeKey = ref(crypto.randomUUID())
const current = computed(() => versions.value.find((item) => item.reportStatus === 'EFFECTIVE'))
const draft = computed(() => versions.value.find((item) => item.reportStatus === 'DRAFT'))

const open = async (id: number) => { visible.value = true; activity.value = undefined; versions.value = []; await load(id) }
const load = async (id: number) => {
  loading.value = true
  try { [activity.value, versions.value] = await Promise.all([ReportApi.getActivity(id), ReportApi.getReportVersions(id)]) } finally { loading.value = false }
}
const reload = async () => { if (!activity.value) return; await load(activity.value.id); emit('changed') }
const openEditor = () => { if (activity.value) editorRef.value?.open(activity.value, draft.value) }
const revoke = async () => {
  if (!activity.value || !current.value) return
  await message.confirm('撤销后不会恢复旧版本，确认继续？')
  await ReportApi.revokeCurrentVersion(activity.value, current.value, revokeKey.value)
  revokeKey.value = crypto.randomUUID()
  message.success('当前报告版本已撤销')
  await reload()
}
const typeLabel = (type: string) => (type === 'FINAL' ? '终验' : '初验')
const activityStatusLabel = (status: string) => ({ PENDING: '待完成', COMPLETED: '已完成' })[status] || status
defineExpose({ open })
</script>

<style scoped lang="scss">
.detail-heading, .section-title, .current-meta, .detail-actions { display: flex; align-items: center; gap: 12px; }
.detail-heading { align-items: flex-start; justify-content: space-between; }
.detail-heading h2, .section-title h3 { margin: 0; color: var(--el-text-color-primary); }
.detail-heading h2 { font-size: 20px; }.section-title h3 { font-size: 16px; }
.detail-heading p, .completion-note { color: var(--el-text-color-secondary); }
.facts, .current-report, .detail-actions, .completion-note { margin-top: 16px; }
.current-report { padding: 16px; border: 1px solid var(--el-border-color-lighter); border-radius: var(--el-border-radius-base); }
.current-meta { flex-wrap: wrap; font-size: 13px; color: var(--el-text-color-secondary); }
.detail-actions { flex-wrap: wrap; }
@media (width <= 767px) { .detail-heading, .detail-actions { align-items: stretch; flex-direction: column; } .detail-actions .el-button { width: 100%; margin-left: 0; } }
</style>
