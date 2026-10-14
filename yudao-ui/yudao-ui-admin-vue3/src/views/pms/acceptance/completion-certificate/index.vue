<template>
  <div class="confirmation-workbench">
    <ContentWrap :body-style="{ padding: '24px', overflow: 'hidden' }">
      <header class="confirmation-heading">
        <div><h2>满意度调查</h2><p>先完善完工证明，再发起调查，客户一次签字确认完工内容与服务评价。</p></div>
        <div class="heading-actions">
          <el-button v-if="!props.readonly" v-hasPermi="['pms:acc-completion-certificate:create']" type="primary" @click="openForm()"><Icon icon="ep:plus" />新增完工证明</el-button>
          <el-dropdown trigger="click">
            <el-button>配置与历史<Icon icon="ep:arrow-down" /></el-button>
            <template #dropdown><el-dropdown-menu>
              <el-dropdown-item v-if="!props.readonly" v-hasPermi="['pms:acceptance:satisfaction:manage']" @click="questionnaireTemplatesVisible = true">问卷模板</el-dropdown-item>
              <el-dropdown-item v-if="!props.readonly" v-hasPermi="['pms:dynamic-form-template:query']" @click="printTemplates?.open()">完工证明打印模板</el-dropdown-item>
              <el-dropdown-item v-if="!props.readonly" v-hasPermi="['pms:dynamic-form-template:query']" @click="satisfactionTemplates?.open()">满意度打印模板</el-dropdown-item>
              <el-dropdown-item divided @click="legacyVisible = true">历史独立调查（只读）</el-dropdown-item>
            </el-dropdown-menu></template>
          </el-dropdown>
        </div>
      </header>
      <ol class="confirmation-sequence" aria-label="办理顺序"><li><Icon icon="ep:document" />完善完工证明</li><li><Icon icon="ep:promotion" />发起并推送调查</li><li><Icon icon="ep:edit-pen" />客户统一签字确认</li></ol>
      <el-form :model="query" inline class="confirmation-query">
        <el-form-item v-if="!props.projectId" label="项目" prop="projectId"><PmsEntitySelect v-model="query.projectId" :api="ProjectApi.getProjectPage" label-field="projectName" value-field="id" query-field="projectName" placeholder="请选择项目" class="!w-180px" /></el-form-item>
        <el-form-item label="项目名称" prop="name"><el-input v-model="query.name" clearable placeholder="搜索项目名称" class="!w-180px" @keyup.enter="load" /></el-form-item>
        <el-form-item label="办理状态" prop="status"><el-select v-model="query.status" clearable placeholder="全部状态" class="!w-160px"><el-option v-for="dict in getIntDictOptions(DICT_TYPE.PMS_COMPLETION_CERT_STATUS)" :key="dict.value" :label="dict.label" :value="dict.value" /></el-select></el-form-item>
        <el-form-item><el-button :loading="loading" @click="load"><Icon icon="ep:search" />查询</el-button></el-form-item>
      </el-form>
    </ContentWrap>
    <ContentWrap :body-style="{ padding: '20px', overflow: 'hidden' }">
      <el-table v-loading="loading" :data="rows" stripe class="desktop-records">
        <el-table-column label="项目名称" min-width="174" show-overflow-tooltip><template #default="{ row }">{{ projectNames[row.projectId] || row.name || '—' }}</template></el-table-column>
        <el-table-column prop="customerUnit" label="客户单位" min-width="135" show-overflow-tooltip />
        <el-table-column label="完工信息" min-width="125"><template #default="{ row }"><div>{{ row.completionDate || '—' }}</div><span class="record-hint">工程师 {{ row.engineerName || '—' }}</span></template></el-table-column>
        <el-table-column label="办理状态" width="112"><template #default="{ row }"><dict-tag :type="DICT_TYPE.PMS_COMPLETION_CERT_STATUS" :value="row.status" /></template></el-table-column>
        <el-table-column label="客户确认" min-width="130"><template #default="{ row }"><span v-if="row.customerSignDate">已确认 · {{ row.customerSignDate }}</span><span v-else class="record-hint">{{ row.status === 0 ? '完善后发起调查' : row.status === 1 ? '待客户统一确认' : '—' }}</span></template></el-table-column>
        <el-table-column label="操作" width="270" fixed="right"><template #default="{ row }"><ConfirmationRecordActions :record="row" :readonly="props.readonly" :busy="pendingActions.has(row.id)" @view="openDetail" @edit="openForm" @action="handleAction" @print="openPrint" @remove="remove" /></template></el-table-column>
        <template #empty><el-empty :image-size="60" description="暂无完工证明。新建并保存完工内容后，即可发起满意度调查。" /></template>
      </el-table>
      <div v-loading="loading" class="mobile-records">
        <el-empty v-if="!loading && !rows.length" :image-size="60" description="先新建完工证明，再发起满意度调查。" />
        <article v-for="row in rows" :key="row.id" class="mobile-record">
          <div class="mobile-record-heading"><h3>{{ projectNames[row.projectId] || row.name }}</h3><dict-tag v-if="row.status != null" :type="DICT_TYPE.PMS_COMPLETION_CERT_STATUS" :value="row.status" /></div>
          <p>{{ row.customerUnit || '—' }}</p>
          <dl><div><dt>完工时间</dt><dd>{{ row.completionDate || '—' }}</dd></div><div><dt>工程师</dt><dd>{{ row.engineerName || '—' }}</dd></div></dl>
          <p class="record-hint">{{ row.customerSignDate ? '已确认 · ' + row.customerSignDate : row.status === 0 ? '保存完工内容后，可发起调查并推送给客户。' : row.status === 1 ? '等待客户填写评价并统一签字。' : '查看完工内容与评价记录。' }}</p>
          <ConfirmationRecordActions :record="row" :readonly="props.readonly" :busy="pendingActions.has(row.id!)" @view="openDetail" @edit="openForm" @action="handleAction" @print="openPrint" @remove="remove" />
        </article>
      </div>
      <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
    </ContentWrap>
  </div>

  <ConfirmationLinkDialog ref="issueDialog" />
  <ConfirmationPrintTemplates ref="printTemplates" kind="COMPLETION" @changed="loadPrintOptions" />
  <ConfirmationPrintTemplates ref="satisfactionTemplates" kind="SATISFACTION" @changed="loadPrintOptions" />
  <ConfirmationPrintPreview ref="printPreview" kind="COMPLETION" :readonly="props.readonly" />
  <ConfirmationPrintPreview
    ref="satisfactionPreview"
    kind="SATISFACTION"
    :readonly="props.readonly"
  />
  <Dialog
    v-model="formVisible"
    :title="form.id ? '编辑完工证明' : '新增完工证明'"
    width="min(860px, 96vw)"
    :before-close="closeForm"
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
  >
    <el-skeleton v-if="formLoading" :rows="10" animated aria-label="正在加载完工证明" />
    <el-result v-else-if="formError" icon="error" title="记录加载失败"
      ><template #extra
        ><el-button @click="openForm(formSource)">重新加载</el-button></template
      ></el-result
    >
    <CompletionCertificateForm
      v-else
      ref="formRef"
      :form="form"
      :devices="deviceRows"
      :project-id="props.projectId"
      :project-name="projectNameCache"
      :disabled="!!props.readonly || saving"
      :print-templates="printOptions"
      v-model:customer-signature="customerSignDataUrl"
      v-model:vendor-signature="vendorSignDataUrl"
      @project-change="onProjectChange"
      @engineer-change="onEngineerChange"
      @add-device="addDeviceRow"
      @remove-device="deviceRows.splice($event, 1)"
    />
    <el-alert v-if="saveError" class="mt-16px" :title="saveError" type="error" :closable="false" />
    <template #footer
      ><el-button :disabled="saving" @click="closeForm()">取消</el-button
      ><el-button
        v-if="!props.readonly"
        type="primary"
        :loading="saving"
        :disabled="formLoading || formError"
        @click="save"
        >保存</el-button
      ></template
    >
  </Dialog>

  <Dialog v-model="questionnaireTemplatesVisible" title="满意度问卷模板" width="min(1100px, 96vw)"><TemplatePanel v-if="questionnaireTemplatesVisible" /></Dialog>
  <Dialog v-model="legacyVisible" title="历史独立调查（只读）" width="min(1100px, 96vw)"><TaskPanel v-if="legacyVisible" :project-id="props.projectId || query.projectId" readonly /></Dialog>
  <el-drawer v-model="detailVisible" title="完工证明与满意度调查" size="min(1000px, 96vw)">
    <el-skeleton v-if="detailLoading" :rows="10" animated aria-label="正在加载完工证明" />
    <el-result v-else-if="detailError" icon="error" title="记录加载失败"
      ><template #extra
        ><el-button @click="detailRow && openDetail(detailRow)">重新加载</el-button></template
      ></el-result
    >
    <div v-else-if="detail" class="confirmation-detail">
      <CompletionCertificateSheet
        :certificate="{ ...detail.certificate, name: detailProjectName || detail.certificate.name }"
        :show-hint="false"
      />
      <dict-tag :type="DICT_TYPE.PMS_COMPLETION_CERT_STATUS" :value="detail.certificate.status!" />
      <section class="detail-survey">
        <h3>满意度调查表</h3>
        <el-tabs v-if="detail.history.length" v-model="historyIndex">
          <el-tab-pane v-for="record in detail.history" :key="record.taskId" :name="String(record.taskId)" :label="`第${record.revisionNo}轮评价`">
            <p v-if="record.submittedAt" class="record-hint">提交时间 {{ formatDate(record.submittedAt) }}<span v-if="record.score != null"> · 满意度 {{ record.score }} 分 · {{ record.passed ? '达标' : '待跟进整改' }}</span></p>
            <p v-else class="record-hint">等待客户填写并确认。</p>
            <QuestionnaireTable v-if="record.questionnaireJson" :definition="JSON.parse(record.questionnaireJson)" :answers="answersFor(record)" disabled :show-signature="false" />
          </el-tab-pane>
        </el-tabs>
        <el-empty v-else :image-size="60" description="完工证明保存后可发起调查；发起时自动生成问卷。" />
      </section>
      <h3>客户确认</h3>
      <p v-if="selectedHistory?.submittedAt"
        >已签字确认 · {{ formatDate(selectedHistory.submittedAt)
        }}<span v-if="selectedHistory.score != null">
          · 满意度 {{ selectedHistory.score }} 分 ·
          {{ selectedHistory.passed ? '达标' : '待跟进整改' }}</span
        ></p
      >
      <p v-else-if="detail.certificate.status === 2 || detail.certificate.status === 3"
        >已确认 · {{ detail.certificate.customerSignDate || '—' }}</p
      >
      <p v-else class="template-hint">{{
        detail.certificate.status === 1
          ? '待客户一次签字统一确认完工证明和满意度调查'
          : detail.certificate.status === 0
            ? '推送时将自动生成满意度调查'
            : '当前记录已驳回'
      }}</p>
      <section
        v-if="detailSignature"
        class="confirmation-signature"
        ><h3>客户统一签字</h3
        ><img
          :src="detailSignature"
          alt="客户手写签字"
        /><el-button
          tag="a"
          :href="detailSignature"
          download="客户手写签字.png"
          link
          type="primary"
          >保存签字图片</el-button
        ></section
      >
      <section v-if="detail.certificate.vendorSignUrl" class="confirmation-signature"
        ><h3>服务方签章</h3><img :src="detail.certificate.vendorSignUrl" alt="服务方签章" /><p
          v-if="detail.certificate.vendorSignDate"
          >{{ detail.certificate.vendorSignDate }}</p
        ></section
      >
      <template v-if="detail.certificate.remark"
        ><h3>备注</h3><p class="confirmation-text">{{ detail.certificate.remark }}</p></template
      >
      <DeliveryPanel
        :key="detail.certificate.id"
        owner-module="ACC"
        entity-type="completionCertificate"
        :entity-id="detail.certificate.id!"
        :type-codes="['COMPLETION_CERTIFICATE']"
        readonly
      />
    </div>
    <template #footer
      ><el-button @click="detailVisible = false">关闭</el-button
      ><el-button
        v-if="detail?.survey"
        :disabled="detail.certificate.status === 4"
        @click="printDetail(detail, 'SATISFACTION')"
        >下载满意度 PDF</el-button
      ><el-button
        type="primary"
        :disabled="!detail || detail.certificate.status === 4"
        @click="detail && printDetail(detail)"
        >下载完工证明 PDF</el-button
      ></template
    >
  </el-drawer>
</template>

<script setup lang="ts">
import ConfirmationLinkDialog from './ConfirmationLinkDialog.vue'
import ConfirmationRecordActions, { type ConfirmationRecordAction, type ConfirmationRecordPrint } from './ConfirmationRecordActions.vue'
import QuestionnaireTable from '../satisfaction/QuestionnaireTable.vue'
import TemplatePanel from '../satisfaction/TemplatePanel.vue'
import TaskPanel from '../satisfaction/TaskPanel.vue'
import CompletionCertificateForm from './CompletionCertificateForm.vue'
import CompletionCertificateSheet from '../satisfaction/CompletionCertificateSheet.vue'
import ConfirmationPrintTemplates from './ConfirmationPrintTemplates.vue'
import ConfirmationPrintPreview from './ConfirmationPrintPreview.vue'
import { completionDisplay, type ConfirmationDocumentKind } from './confirmationPrintForm'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import DeliveryPanel from '@/components/BusinessEntity/DeliveryPanel.vue'
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import { useUserStore } from '@/store/modules/user'
import * as CompletionCertificateApi from '@/api/pms/acceptance/completion-certificate'
import * as ProjectApi from '@/api/pms/project/projects'
import * as UserProfileApi from '@/api/system/user/profile'
import { loadProjectEquipmentList, type ProjectEquipmentProduct } from '@/api/pms/commerce/projectEquipment'
import * as FileApi from '@/api/infra/file'
import * as DynamicFormApi from '@/api/pms/platform/dynamic-form'
import { checkPermi } from '@/utils/permission'
import { signaturePngFile } from '@/components/FormCreate/src/customerConfirmation'
import { formatDate } from '@/utils/formatTime'
import type { CompletionCertificateVO } from '@/api/pms/acceptance/completion-certificate'

defineOptions({ name: 'PmsCompletionCertificate' })
const props = defineProps<{ projectId?: number; readonly?: boolean }>()
const emit = defineEmits<{ changed: []; 'dirty-change': [value: boolean] }>()
const issueDialog = ref<InstanceType<typeof ConfirmationLinkDialog>>()
const message = useMessage()
const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const pendingActions = reactive(new Set<number>())
let context = 0,
  loadRequest = 0,
  formRequest = 0,
  detailRequest = 0,
  printOptionsRequest = 0
const rows = ref<CompletionCertificateVO[]>([])
const projectNames = reactive<Record<number, string>>({})
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: props.projectId as number | undefined,
  name: '',
  status: undefined
})
const formVisible = ref(false)
const formLoading = ref(false),
  formError = ref(false),
  saveError = ref('')
const formSource = ref<CompletionCertificateVO>()
const formRef = ref<InstanceType<typeof CompletionCertificateForm>>()
const printTemplates = ref<InstanceType<typeof ConfirmationPrintTemplates>>()
const satisfactionTemplates = ref<InstanceType<typeof ConfirmationPrintTemplates>>()
const questionnaireTemplatesVisible = ref(false)
const legacyVisible = ref(false)
const historyIndex = ref('')
const answersFor = (record: CompletionCertificateApi.CompletionSurveyVO) => Object.fromEntries((JSON.parse(record.answerSnapshot || '{"answers":[]}').answers as { questionCode: string; value: string | string[] }[]).map(item => [item.questionCode, item.value]))
const printPreview = ref<InstanceType<typeof ConfirmationPrintPreview>>()
const satisfactionPreview = ref<InstanceType<typeof ConfirmationPrintPreview>>()
const printOptions = ref<DynamicFormApi.DynamicFormSelectionVO[]>([])
// 签章复用培训客户确认/满意度问卷的 form-create signaturePad 手写控件：
// 控件值为 PNG dataURL，保存时经 signaturePngFile 转 File 走既有文件上传链路，实体列落文件 URL。
const customerSignDataUrl = ref('')
const vendorSignDataUrl = ref('')
// 表单字段即实体列（V386 起完工证明附加字段实体化、设备明细子表），支持统计取数。
// 历史记录的 remark/content JSON 信封仅在实体列全空时读取回显，编辑保存后落实体列。
interface CompletionCertEnvelope {
  serviceType?: string
  engineer?: string
  engineerUserId?: number
  contact?: string
  customerUnit?: string
  contractNo?: string
  items?: Record<string, string>
  devices?: { type?: string; model?: string; qty?: number }[]
  customerSignDate?: string
  customerSignUrl?: string
  vendorSignDate?: string
  vendorSignUrl?: string
}
interface CertDeviceRow {
  deviceType: string
  deviceModel: string
  quantity: number
}
const certServiceItems = [
  { field: 'itemArrival', label: '① 完成到货验收' },
  { field: 'itemInstall', label: '② 完成设备硬件安装和软件调测' },
  { field: 'itemCutover', label: '③ 完成业务上线/割接且业务测试正常' },
  { field: 'itemTraining', label: '④ 完成产品维护现场讲解和培训' },
  { field: 'itemDocs', label: '⑤ 工程文档、帐号密码已移交并协助修改' }
] as const
const deviceRows = ref<CertDeviceRow[]>([])
const projectNameCache = ref('')
const form = reactive<CompletionCertificateVO>({
  projectId: props.projectId as number,
  name: '',
  code: '',
  itemArrival: '',
  itemInstall: '',
  itemCutover: '',
  itemTraining: '',
  itemDocs: '',
  content: '',
  remark: ''
})
const parseCertEnvelope = (raw: string | undefined | null): CompletionCertEnvelope | null => {
  try {
    const v = raw ? JSON.parse(raw) : null
    return v && typeof v === 'object' && !Array.isArray(v) ? v : null
  } catch {
    return null
  }
}
// 旧记录（信封在 remark 或 content）读取兼容：实体列全空时按信封回显
const applyLegacyEnvelope = () => {
  const hasEntityData =
    form.serviceType ||
    form.engineerName ||
    form.engineerContact ||
    form.customerUnit ||
    form.contractNo ||
    certServiceItems.some(({ field }) => form[field]) ||
    deviceRows.value.length ||
    form.customerSignUrl ||
    form.vendorSignUrl
  if (hasEntityData) return
  const envelope = parseCertEnvelope(form.content) ?? parseCertEnvelope(form.remark)
  if (!envelope) return
  form.serviceType = envelope.serviceType ?? ''
  form.engineerName = envelope.engineer ?? ''
  form.engineerUserId = envelope.engineerUserId
  form.engineerContact = envelope.contact ?? ''
  form.customerUnit = envelope.customerUnit ?? ''
  form.contractNo = envelope.contractNo ?? ''
  const legacyItems = envelope.items ?? {}
  const legacyKeys = ['i1', 'i2', 'i3', 'i4', 'i5'] as const
  certServiceItems.forEach(({ field }, index) => {
    form[field] = legacyItems[legacyKeys[index]] ?? ''
  })
  deviceRows.value = (envelope.devices ?? []).map((d) => ({
    deviceType: String(d?.type ?? ''),
    deviceModel: String(d?.model ?? ''),
    quantity: Number(d?.qty ?? 1) || 1
  }))
  form.customerSignDate = envelope.customerSignDate ?? ''
  customerSignDataUrl.value = envelope.customerSignUrl ?? ''
  form.vendorSignDate = envelope.vendorSignDate ?? ''
  vendorSignDataUrl.value = envelope.vendorSignUrl ?? ''
}

const load = async () => {
  const request = ++loadRequest,
    current = context
  loading.value = true
  try {
    const data = await CompletionCertificateApi.getConfirmationPage({ ...query })
    if (request !== loadRequest || current !== context) return
    rows.value = data.list
    total.value = data.total
    await Promise.all(
      [...new Set(rows.value.map((row) => row.projectId))].map(async (projectId) => {
        const project = await ProjectApi.getProject(projectId)
        if (request === loadRequest && current === context)
          projectNames[projectId] = project.projectName ?? ''
      })
    )
  } finally {
    if (request === loadRequest) loading.value = false
  }
}
const loadPrintOptions = async () => {
  if (!checkPermi(['pms:dynamic-form-template:query'])) return
  const request = ++printOptionsRequest,
    current = context
  const all: DynamicFormApi.DynamicFormSelectionVO[] = []
  for (let pageNo = 1; ; pageNo++) {
    const page = await DynamicFormApi.getTemplateSelection({ pageNo, pageSize: 100 })
    if (request !== printOptionsRequest || current !== context) return
    all.push(...page.list)
    if (all.length >= page.total || !page.list.length) break
  }
  printOptions.value = all.filter((item) =>
    ['COMPLETION_PRINT', 'SATISFACTION_PRINT'].includes(item.categoryCode || '')
  )
}
const isCurrentForm = (request: number, projectId?: number) =>
  request === formRequest &&
  formVisible.value &&
  (projectId === undefined || form.projectId === projectId)
const fillDevicesFromProject = (products: ProjectEquipmentProduct[]) => {
  deviceRows.value = products.map((product) => ({
    deviceType: product.productName,
    deviceModel: product.productModel,
    quantity: product.deviceCount
  }))
}
const resolveProject = async (request: number, projectId?: number) => {
  if (!projectId) return
  const project = await ProjectApi.getProject(projectId)
  if (!isCurrentForm(request, projectId)) return
  projectNameCache.value = project.projectName ?? ''
  form.customerUnit = form.customerUnit || project.customerName || ''
  if (!form.contractNo) {
    form.contractNo = project.contractNo ?? ''
  }
  if (!form.contractNo || (!form.id && !deviceRows.value.length)) {
    const equipment = await loadProjectEquipmentList(projectId, () => isCurrentForm(request, projectId))
    if (!equipment) return
    if (!form.contractNo) {
      const contractNos = new Set(equipment.scopes.map((scope) => scope.contractNo).filter(Boolean))
      if (contractNos.size === 1) form.contractNo = [...contractNos][0]
    }
    if (!form.id && !deviceRows.value.length) fillDevicesFromProject(equipment.list)
  }
}
const prefillCurrentEngineer = async (request: number) => {
  const loginUser = userStore.getUser
  form.engineerName = loginUser.nickname ?? ''
  form.engineerUserId = loginUser.id || undefined
  try {
    const current = await UserProfileApi.getUserProfile()
    if (!isCurrentForm(request)) return
    form.engineerName = current.nickname ?? loginUser.nickname ?? ''
    form.engineerUserId = current.id
    form.engineerContact = current.mobile ?? ''
  } catch {
    // 个人资料暂不可读时保留登录用户姓名，联系方式仍可手填。
  }
}
const onProjectChange = async (projectId?: number) => {
  const request = ++formRequest
  projectNameCache.value = ''
  form.customerUnit = ''
  form.contractNo = ''
  deviceRows.value = []
  formLoading.value = true
  try {
    await resolveProject(request, projectId)
  } catch {
    if (isCurrentForm(request)) formError.value = true
  } finally {
    if (isCurrentForm(request)) formLoading.value = false
  }
}
const onEngineerChange = (_nickname: string, selected?: any) => {
  form.engineerUserId = selected?.id
  form.engineerContact = selected?.mobile ?? ''
}
// 手写控件值为 dataURL 时转文件上传；编辑回显的文件 URL 原样保留
const uploadSignature = async (value: string): Promise<string> => {
  if (!value) return ''
  if (!value.startsWith('data:image/png;base64,')) return value
  const res = await FileApi.updateFile({ file: signaturePngFile(value) })
  return res.data
}
const addDeviceRow = () => deviceRows.value.push({ deviceType: '', deviceModel: '', quantity: 1 })
const openForm = async (row?: CompletionCertificateVO) => {
  if (props.readonly || saving.value) return
  if (row && row.status !== 0) return openDetail(row)
  const request = ++formRequest
  formSource.value = row
  formLoading.value = true
  formError.value = false
  saveError.value = ''
  // 重置为全新表单基准，再叠加编辑数据
  Object.assign(
    form,
    {
      id: undefined,
      projectId: (props.projectId ?? undefined) as number,
      name: '',
      code: '',
      customerId: undefined,
      completionDate: row ? '' : formatDate(new Date(), 'YYYY-MM-DD'),
      serviceType: '',
      engineerUserId: undefined,
      engineerName: '',
      engineerContact: '',
      customerUnit: '',
      contractNo: '',
      itemArrival: '',
      itemInstall: '',
      itemCutover: '',
      itemTraining: '',
      itemDocs: '',
      customerSignDate: '',
      customerSignUrl: '',
      vendorSignDate: '',
      vendorSignUrl: '',
      devices: [],
      content: '',
      attachmentUrl: '',
      status: 0,
      remark: '',
      version: undefined,
      satisfactionTemplateId: undefined,
      completionPrintTemplateId: undefined,
      completionPrintSnapshot: undefined,
      satisfactionPrintTemplateId: undefined,
      satisfactionPrintSnapshot: undefined
    },
    row || {}
  )
  customerSignDataUrl.value = ''
  vendorSignDataUrl.value = ''
  projectNameCache.value = ''
  deviceRows.value = []
  formVisible.value = true
  try {
    if (form.id) {
      // 设备明细子表随详情返回，编辑时拉全量数据再套旧信封兼容
      const joint = await CompletionCertificateApi.getConfirmation(form.id)
      if (!isCurrentForm(request)) return
      const detail = joint.certificate
      Object.assign(form, detail)
      deviceRows.value = (detail.devices ?? []).map((d) => ({
        deviceType: d.deviceType ?? '',
        deviceModel: d.deviceModel ?? '',
        quantity: d.quantity ?? 1
      }))
      // 签章控件值为 dataURL/文件 URL，编辑时从实体列回种，未重新签名则原样保留
      customerSignDataUrl.value = joint.survey?.signatureImageDataUrl || form.customerSignUrl || ''
      vendorSignDataUrl.value = form.vendorSignUrl ?? ''
      applyLegacyEnvelope()
      if (form.projectId) await resolveProject(request, form.projectId)
    } else {
      await Promise.all([prefillCurrentEngineer(request), resolveProject(request, props.projectId)])
    }
    await loadPrintOptions()
    if (!isCurrentForm(request)) return
    if (!form.id) {
      form.completionPrintTemplateId = printOptions.value.find(
        (item) => item.templateCode === 'COMPLETION_PRINT_WORD_V2'
      )?.templateId
      form.satisfactionPrintTemplateId = printOptions.value.find(
        (item) => item.templateCode === 'SATISFACTION_PRINT_WORD_V2'
      )?.templateId
    }
  } catch {
    if (isCurrentForm(request)) formError.value = true
  } finally {
    if (isCurrentForm(request)) formLoading.value = false
  }
}
const closeForm = (done?: () => void) => {
  if (saving.value) return
  formRequest++
  formVisible.value = false
  done?.()
}
const save = async () => {
  if (
    props.readonly ||
    saving.value ||
    formLoading.value ||
    formError.value ||
    !formVisible.value ||
    (form.id && form.status !== 0)
  )
    return
  const current = context,
    request = formRequest
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (props.readonly || saving.value || current !== context || !isCurrentForm(request)) return
  saving.value = true
  saveError.value = ''
  try {
    // 证明名称即项目名称，随项目带入，不再单独填写
    if (!form.name && (projectNameCache.value || form.projectId)) {
      if (!projectNameCache.value && form.projectId) {
        projectNameCache.value = (await ProjectApi.getProject(form.projectId)).projectName ?? ''
      }
      form.name = projectNameCache.value
    }
    form.devices = deviceRows.value.map((device) => ({ ...device }))
    form.customerSignUrl = await uploadSignature(customerSignDataUrl.value)
    form.vendorSignUrl = await uploadSignature(vendorSignDataUrl.value)
    if (props.readonly || current !== context || !isCurrentForm(request)) return
    await CompletionCertificateApi.saveConfirmation({ ...form })
    if (props.readonly || current !== context || !isCurrentForm(request)) return
    message.success('保存成功')
    formVisible.value = false
    emit('changed')
    await load()
  } catch (error) {
    if (current === context && isCurrentForm(request))
      saveError.value = error instanceof Error ? error.message : '保存失败，请重试'
  } finally {
    saving.value = false
  }
}
const remove = async (row: CompletionCertificateVO) => {
  if (props.readonly || row.status !== 0 || pendingActions.has(row.id!)) return
  const current = context
  await message.delConfirm()
  if (props.readonly || current !== context || pendingActions.has(row.id!)) return
  pendingActions.add(row.id!)
  try {
    await CompletionCertificateApi.deleteConfirmation(row.id!)
    if (props.readonly || current !== context) return
    message.success('删除成功')
    emit('changed')
    await load()
  } finally {
    pendingActions.delete(row.id!)
  }
}
const handleAction = async ({ row, action, actionText }: ConfirmationRecordAction) => {
  if (props.readonly || pendingActions.has(row.id!)) return
  const current = context
  await message.confirm(action === 'issueConfirmation' ? `确认以完工证明【${row.name}】${actionText}并推送给客户？` : `确认${actionText}完工证明【${row.name}】？`)
  if (props.readonly || current !== context || pendingActions.has(row.id!)) return
  pendingActions.add(row.id!)
  try {
    if (action === 'issueConfirmation') {
      const issued = await CompletionCertificateApi.issueConfirmation(row.id!)
      if (current !== context || props.readonly) return
      issueDialog.value?.open(issued)
    } else await CompletionCertificateApi[action](row.id!)
    if (current !== context || props.readonly) return
    message.success(`${actionText}成功`)
    emit('changed')
    await load()
  } finally {
    pendingActions.delete(row.id!)
  }
}

const detailVisible = ref(false),
  detailLoading = ref(false),
  detailError = ref(false)
const detail = ref<CompletionCertificateApi.CompletionConfirmationDetail>(),
  detailRow = ref<CompletionCertificateVO>()
const detailProjectName = ref('')
const selectedHistory = computed(() => detail.value?.history.find(record => String(record.taskId) === historyIndex.value) ?? detail.value?.survey)
const detailSignature = computed(() => selectedHistory.value ? selectedHistory.value.signatureImageDataUrl : detail.value?.certificate.customerSignUrl)
const openDetail = async (row: CompletionCertificateVO) => {
  const request = ++detailRequest,
    current = context
  detailRow.value = row
  detail.value = undefined
  detailProjectName.value = ''
  detailError.value = false
  detailVisible.value = true
  detailLoading.value = true
  try {
    const data = await CompletionCertificateApi.getConfirmation(row.id!)
    const project = await ProjectApi.getProject(data.certificate.projectId)
    if (request === detailRequest && current === context && detailVisible.value) {
      detail.value = { ...data, certificate: completionDisplay(data.certificate) }
      detailProjectName.value = project.projectName || data.certificate.name
      historyIndex.value = String(data.history[0]?.taskId || '')
    }
  } catch {
    if (request === detailRequest && current === context) detailError.value = true
  } finally {
    if (request === detailRequest) detailLoading.value = false
  }
}
const printDetail = async (
  data: CompletionCertificateApi.CompletionConfirmationDetail,
  kind: ConfirmationDocumentKind = 'COMPLETION'
) => {
  const raw =
    kind === 'COMPLETION'
      ? data.certificate.completionPrintSnapshot
      : data.certificate.satisfactionPrintSnapshot
  await (kind === 'COMPLETION' ? printPreview.value : satisfactionPreview.value)?.open(
    { ...data, certificate: completionDisplay(data.certificate) },
    raw ? JSON.parse(raw) : undefined
  )
}
const openPrint = async ({ row, kind }: ConfirmationRecordPrint) => {
  const current = context
  const data = await CompletionCertificateApi.getConfirmation(row.id!)
  if (current === context) await printDetail(data, kind)
}
const isExporting = () =>
  !!printPreview.value?.isExporting() || !!satisfactionPreview.value?.isExporting()
const isDirty = () => formVisible.value || saving.value || isExporting()
const requestLeave = () => {
  if (!isDirty()) return true
  message.warning(isExporting() ? '请等待 PDF 登记完成' : '请先保存或关闭完工证明')
  return false
}
const beforeUnload = (event: BeforeUnloadEvent) => {
  if (isDirty()) {
    event.preventDefault()
    event.returnValue = ''
  }
}
onBeforeRouteLeave(requestLeave)
onBeforeRouteUpdate(requestLeave)
watch(isDirty, (value) => emit('dirty-change', value))
watch(
  () => [props.projectId, props.readonly],
  () => {
    context++
    formRequest++
    detailRequest++
    printOptionsRequest++
    formVisible.value = false
    detailVisible.value = false
    detail.value = undefined
    issueDialog.value?.close()
    legacyVisible.value = false
    questionnaireTemplatesVisible.value = false
    printPreview.value?.close()
    satisfactionPreview.value?.close()
    query.projectId = props.projectId
    query.pageNo = 1
    rows.value = []
    void load()
  }
)
defineExpose({
  requestLeave,
  isDirty,
  discardChanges: () => {
    if (saving.value || isExporting()) return false
    closeForm()
    return true
  }
})
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
onMounted(load)
</script>

<style scoped>
.confirmation-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
}
.confirmation-heading h2 {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}
.confirmation-heading p,
.template-hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.7;
}
.confirmation-detail h3 {
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 20px;
  font-size: 14px;
}
.confirmation-text {
  white-space: pre-wrap;
  line-height: 1.7;
}
.confirmation-signature img {
  display: block;
  width: 100%;
  max-height: 200px;
  object-fit: contain;
  margin: 12px 0;
  border: 1px solid var(--el-border-color-lighter);
  background: white;
}
.heading-actions { display: flex; flex-shrink: 0; gap: 10px; flex-wrap: wrap; }
.confirmation-sequence { display: flex; align-items: center; gap: 24px; margin: 0 0 24px; padding: 14px 16px; list-style: none; border: 1px solid var(--el-border-color-lighter); border-radius: 4px; background: var(--el-fill-color-lighter); }
.confirmation-sequence li { display: flex; align-items: center; gap: 8px; color: var(--el-text-color-regular); font-size: 13px; }
.confirmation-sequence li + li { padding-left: 24px; border-left: 1px solid var(--el-border-color); }
.confirmation-query { margin-bottom: -18px; }
.record-hint { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.6; }
.mobile-records { display: none; }
.detail-survey { margin-top: 24px; }
.confirmation-detail :deep(.completion-sheet h2) { font-size: 18px; }
@media (width <= 900px) {
  .confirmation-heading { flex-direction: column; gap: 16px; }
  .heading-actions { width: 100%; }
  .heading-actions .el-button { min-height: 44px; }
  .confirmation-sequence { gap: 8px; flex-direction: column; align-items: flex-start; }
  .confirmation-sequence li + li { padding-left: 0; border-left: 0; }
  .confirmation-query :deep(.el-form-item) { width: 100%; margin-right: 0; }
  .confirmation-query :deep(.el-form-item__content) { min-width: 0; }
  .confirmation-query :deep(.el-input), .confirmation-query :deep(.el-select) { width: 100% !important; }
  .desktop-records { display: none; }
  .mobile-records { display: block; }
  .mobile-record { padding: 20px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
  .mobile-record:first-child { padding-top: 0; }
  .mobile-record-heading { display: flex; align-items: flex-start; gap: 12px; justify-content: space-between; }
  .mobile-record-heading h3 { margin: 0; font-size: 16px; line-height: 1.6; overflow-wrap: anywhere; }
  .mobile-record > p { margin: 8px 0 12px; font-size: 14px; }
  .mobile-record dl { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin: 0 0 12px; font-size: 13px; }
  .mobile-record dt { margin-bottom: 4px; color: var(--el-text-color-secondary); }
  .mobile-record dd { margin: 0; }
}
</style>
