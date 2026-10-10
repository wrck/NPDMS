<template>
  <ContentWrap :body-style="{ padding: '20px', overflow: 'hidden' }">
    <header class="confirmation-heading">
      <h2>满意度调查表</h2>
      <p>推送完工证明时自动生成调查表，客户一次签字统一提交两份表单。</p>
    </header>
    <el-form :model="query" inline class="-mb-15px">
      <el-form-item v-if="!props.projectId" label="项目"><PmsEntitySelect v-model="query.projectId" :api="ProjectApi.getProjectPage" label-field="projectName" value-field="id" query-field="projectName" class="!w-180px" /></el-form-item>
      <el-form-item label="名称"><el-input v-model="query.name" clearable class="!w-180px" @keyup.enter="load" /></el-form-item>
      <el-form-item label="状态"><el-select v-model="query.status" clearable class="!w-140px"><el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item>
        <el-button v-hasPermi="['pms:dynamic-form-template:query']" @click="printTemplates?.open()">打印模板配置</el-button>
        <el-button v-hasPermi="['pms:acceptance:satisfaction:manage']" @click="questionnaireTemplatesVisible = true">问卷模板配置</el-button>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button @click="legacyVisible = true">历史独立调查</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap :body-style="{ padding: '20px', overflow: 'hidden' }">
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="name" label="关联完工证明" min-width="200" show-overflow-tooltip />
      <el-table-column prop="code" label="证明编号" min-width="150" />
      <el-table-column prop="completionDate" label="完工日期" width="110" />
      <el-table-column prop="engineerName" label="工程师" width="110" />
      <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.status === 2 || row.status === 3 ? 'success' : row.status === 1 ? 'warning' : 'info'">{{ statusLabel(row) }}</el-tag></template></el-table-column>
      <el-table-column label="客户确认" min-width="170"><template #default="{ row }"><span v-if="row.customerSignDate">已签字确认 · {{ row.customerSignDate }}</span><span v-else class="text-gray-400">{{ row.status === 1 ? '待客户统一确认' : '—' }}</span></template></el-table-column>
      <el-table-column label="操作" width="190" fixed="right"><template #default="{ row }">
        <el-button link type="primary" @click="openDetail(row)">查看</el-button>
        <el-button link type="primary" :disabled="row.status === 4" @click="openPrint(row)">下载 PDF</el-button>
      </template></el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>
  <el-drawer v-model="detailVisible" title="满意度调查表" size="min(860px, 96vw)">
    <el-skeleton v-if="detailLoading" :rows="10" animated />
    <el-result v-else-if="detailError" icon="error" title="记录加载失败"><template #extra><el-button @click="detailRow && openDetail(detailRow)">重新加载</el-button></template></el-result>
    <div v-else-if="detail" class="confirmation-detail">
      <h2>{{ detail.certificate.name }}</h2><el-tag>{{ statusLabel(detail.certificate) }}</el-tag>
      <h3>客户评价与确认</h3>
      <el-tabs v-if="detail.history.length" v-model="historyIndex"><el-tab-pane v-for="record in detail.history" :key="record.taskId" :name="String(record.taskId)" :label="`第${record.revisionNo}轮`">
        <p v-if="record.submittedAt">已签字确认 · {{ formatDate(record.submittedAt) }}<span v-if="record.score != null"> · 得分 {{ record.score }} · {{ record.passed ? '达标' : '待跟进整改' }}</span></p>
        <p v-else class="template-hint">尚无客户评价与签字确认</p>
        <QuestionnaireTable v-if="record.questionnaireJson" :definition="JSON.parse(record.questionnaireJson)" :answers="answersFor(record)" disabled :show-signature="false" />
        <section v-if="record.signatureImageDataUrl" class="confirmation-signature"><h3>客户手写签字</h3><img :src="record.signatureImageDataUrl" alt="客户手写签字" /><el-button tag="a" :href="record.signatureImageDataUrl" download="客户手写签字.png" link type="primary">保存签字图片</el-button></section>
      </el-tab-pane></el-tabs>
      <el-empty v-else description="推送完工证明时将自动生成满意度调查" :image-size="60" />
      <template v-if="detail.certificate.remark"><h3>备注</h3><p class="confirmation-text">{{ detail.certificate.remark }}</p></template>
      <DeliveryPanel :key="detail.certificate.id" owner-module="ACC" entity-type="completionCertificate" :entity-id="detail.certificate.id!" :type-codes="['COMPLETION_CERTIFICATE']" readonly />
    </div>
    <template #footer>
      <el-button @click="detailVisible = false">关闭</el-button>
      <el-button :disabled="!detail || detail.certificate.status === 4" @click="detail && printDetail(detail, 'COMPLETION')">下载完工证明 PDF</el-button>
      <el-button type="primary" :disabled="!detail || detail.certificate.status === 4" @click="detail && printDetail(detail)">下载满意度 PDF</el-button>
    </template>
  </el-drawer>
  <ConfirmationPrintTemplates ref="printTemplates" kind="SATISFACTION" />
  <ConfirmationPrintPreview ref="printPreview" kind="SATISFACTION" :readonly="props.readonly" />
  <ConfirmationPrintPreview ref="completionPreview" kind="COMPLETION" :readonly="props.readonly" />
  <Dialog v-model="questionnaireTemplatesVisible" title="满意度问卷模板配置" width="min(1100px, 96vw)"><TemplatePanel /></Dialog>
  <Dialog v-model="legacyVisible" title="历史独立满意度调查（只读）" width="min(1100px, 96vw)"><TaskPanel v-if="legacyVisible" :project-id="props.projectId" readonly /></Dialog>
</template>

<script setup lang="ts">
import * as Api from '@/api/pms/acceptance/completion-certificate'
import * as ProjectApi from '@/api/pms/project/projects'
import QuestionnaireTable from '../satisfaction/QuestionnaireTable.vue'
import TemplatePanel from '../satisfaction/TemplatePanel.vue'
import TaskPanel from '../satisfaction/TaskPanel.vue'
import DeliveryPanel from '@/components/BusinessEntity/DeliveryPanel.vue'
import ConfirmationPrintTemplates from './ConfirmationPrintTemplates.vue'
import ConfirmationPrintPreview from './ConfirmationPrintPreview.vue'
import { completionDisplay, type ConfirmationDocumentKind } from './confirmationPrintForm'
import { formatDate } from '@/utils/formatTime'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
const props = defineProps<{ projectId?: number; readonly?: boolean }>()
const emit = defineEmits<{ changed: []; 'dirty-change': [value: boolean] }>()
const message = useMessage()
const query = reactive({ pageNo: 1, pageSize: 10, projectId: props.projectId, name: '', status: undefined as number | undefined })
const rows = ref<Api.CompletionCertificateVO[]>([]), total = ref(0), loading = ref(false)
const statuses = [{ value: 1, label: '已外发' }, { value: 2, label: '客户已确认' }, { value: 3, label: '已归档' }, { value: 4, label: '已作废/驳回' }]
const statusLabel = (row: Api.CompletionCertificateVO) => statuses.find(item => item.value === row.status)?.label || ''
const printTemplates = ref<InstanceType<typeof ConfirmationPrintTemplates>>()
const printPreview = ref<InstanceType<typeof ConfirmationPrintPreview>>()
const completionPreview = ref<InstanceType<typeof ConfirmationPrintPreview>>()
const questionnaireTemplatesVisible = ref(false), legacyVisible = ref(false)
let context = 0, loadRequest = 0, detailRequest = 0
const load = async () => {
  const request = ++loadRequest, current = context
  loading.value = true
  try { const page = await Api.getConfirmationPage({ ...query, survey: true }); if (request === loadRequest && current === context) { rows.value = page.list; total.value = page.total } }
  finally { if (request === loadRequest) loading.value = false }
}
const detailVisible = ref(false), detailLoading = ref(false), detailError = ref(false), historyIndex = ref('')
const detail = ref<Api.CompletionConfirmationDetail>(), detailRow = ref<Api.CompletionCertificateVO>()
const openDetail = async (row: Api.CompletionCertificateVO) => {
  const request = ++detailRequest, current = context
  detailRow.value = row; detail.value = undefined; detailError.value = false; detailVisible.value = true; detailLoading.value = true
  try {
    const data = await Api.getConfirmation(row.id!)
    if (request === detailRequest && current === context) { detail.value = { ...data, certificate: completionDisplay(data.certificate) }; historyIndex.value = String(data.history[0]?.taskId || '') }
  } catch { if (request === detailRequest) detailError.value = true }
  finally { if (request === detailRequest) detailLoading.value = false }
}
const answersFor = (record: Api.CompletionSurveyVO) => Object.fromEntries((JSON.parse(record.answerSnapshot || '{"answers":[]}').answers as { questionCode: string; value: string | string[] }[]).map(item => [item.questionCode, item.value]))
const printDetail = async (data: Api.CompletionConfirmationDetail, kind: ConfirmationDocumentKind = 'SATISFACTION') => {
  const raw = kind === 'SATISFACTION' ? data.certificate.satisfactionPrintSnapshot : data.certificate.completionPrintSnapshot
  const preview = kind === 'SATISFACTION' ? printPreview.value : completionPreview.value
  await preview?.open({ ...data, certificate: completionDisplay(data.certificate) }, raw ? JSON.parse(raw) : undefined)
}
const openPrint = async (row: Api.CompletionCertificateVO) => { const current = context; const data = await Api.getConfirmation(row.id!); if (current === context) await printDetail(data) }
const isExporting = () => !!printPreview.value?.isExporting() || !!completionPreview.value?.isExporting()
const requestLeave = () => { if (!isExporting()) return true; message.warning('请等待 PDF 登记完成'); return false }
const beforeUnload = (event: BeforeUnloadEvent) => { if (isExporting()) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(requestLeave)
onBeforeRouteUpdate(requestLeave)
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
watch(isExporting, value => emit('dirty-change', value))
watch(() => [props.projectId, props.readonly], () => { context++; loadRequest++; detailRequest++; query.projectId = props.projectId; query.pageNo = 1; detailVisible.value = false; detail.value = undefined; printPreview.value?.close(); completionPreview.value?.close(); void load() })
onMounted(load)
defineExpose({ requestLeave, isDirty: isExporting, discardChanges: () => !isExporting() })
</script>

<style scoped>
.confirmation-heading { margin-bottom: 20px; }
.confirmation-heading h2, .confirmation-detail h2 { margin: 0 0 6px; font-size: 16px; font-weight: 600; }
.confirmation-heading p, .template-hint { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.7; }
.confirmation-detail h3 { border-top: 1px solid var(--el-border-color-lighter); padding-top: 20px; font-size: 14px; }
.confirmation-text { white-space: pre-wrap; line-height: 1.7; }
.confirmation-signature img { display: block; width: 100%; max-height: 200px; object-fit: contain; margin: 12px 0; border: 1px solid var(--el-border-color-lighter); background: white; }
</style>
