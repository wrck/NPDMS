<template>
  <ContentWrap :body-style="{ padding: '20px', overflow: 'hidden' }">
    <header class="confirmation-heading"
      ><h2>完工证明</h2
      ><p>记录完工内容，推送时自动生成满意度调查，客户一次签字统一提交两份表单。</p></header
    >
    <el-form :model="query" inline class="-mb-15px">
      <el-form-item v-if="!props.projectId" label="项目" prop="projectId">
        <PmsEntitySelect
          v-model="query.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="projectName"
          value-field="id"
          query-field="projectName"
          placeholder="请选择项目"
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item label="名称" prop="name"
        ><el-input v-model="query.name" clearable class="!w-180px" @keyup.enter="load"
      /></el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px"
          ><el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_COMPLETION_CERT_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
        /></el-select>
      </el-form-item>
      <el-form-item>
        <el-button v-hasPermi="['pms:dynamic-form-template:query']" @click="printTemplates?.open()"
          >打印模板配置</el-button
        >
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button
          v-if="!props.readonly"
          v-hasPermi="['pms:acc-completion-certificate:create']"
          type="primary"
          @click="openForm()"
          ><Icon icon="ep:plus" />新增完工证明</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap :body-style="{ padding: '20px', overflow: 'hidden' }">
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column label="项目名称" min-width="180" show-overflow-tooltip
        ><template #default="{ row }">{{
          projectNames[row.projectId] || '—'
        }}</template></el-table-column
      >
      <el-table-column prop="customerUnit" label="客户单位" min-width="180" show-overflow-tooltip />
      <el-table-column prop="completionDate" label="完工时间" width="110" />
      <el-table-column prop="engineerName" label="工程师" width="110" />
      <el-table-column prop="status" label="状态" width="110"
        ><template #default="{ row }"
          ><dict-tag :type="DICT_TYPE.PMS_COMPLETION_CERT_STATUS" :value="row.status" /></template
      ></el-table-column>
      <el-table-column label="客户确认" min-width="170"
        ><template #default="{ row }"
          ><span v-if="row.customerSignDate">已签字确认 · {{ row.customerSignDate }}</span
          ><span v-else class="text-gray-400">{{
            row.status === 1 ? '待客户统一确认' : '—'
          }}</span></template
        ></el-table-column
      >
      <el-table-column label="操作" width="400" fixed="right"
        ><template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          <el-button
            v-if="!props.readonly && row.status === 0"
            v-hasPermi="['pms:acc-completion-certificate:update']"
            link
            type="primary"
            @click="openForm(row)"
            >编辑</el-button
          >
          <el-button
            v-if="!props.readonly && (row.status === 0 || row.status === 1)"
            v-hasPermi="['pms:acc-completion-certificate:submit']"
            link
            type="success"
            :loading="pendingActions.has(row.id)"
            @click="
              handleAction(row, 'issueConfirmation', row.status === 1 ? '重新推送' : '推送至客户')
            "
            >{{ row.status === 1 ? '重新推送' : '推送至客户' }}</el-button
          >
          <el-button link type="primary" :disabled="row.status === 4" @click="openPrint(row)"
            >下载 PDF</el-button
          >
          <el-button
            v-if="!props.readonly && row.status === 1"
            v-hasPermi="['pms:acc-completion-certificate:audit']"
            link
            type="danger"
            :disabled="pendingActions.has(row.id)"
            @click="handleAction(row, 'rejectCompletionCertificate', '驳回')"
            >驳回</el-button
          >
          <el-button
            v-if="!props.readonly && row.status === 2"
            v-hasPermi="['pms:acc-completion-certificate:audit']"
            link
            type="primary"
            :disabled="pendingActions.has(row.id)"
            @click="handleAction(row, 'archiveCompletionCertificate', '归档')"
            >归档</el-button
          >
          <el-button
            v-if="!props.readonly && row.status === 0"
            v-hasPermi="['pms:acc-completion-certificate:delete']"
            link
            type="danger"
            :disabled="pendingActions.has(row.id)"
            @click="remove(row)"
            >删除</el-button
          >
        </template></el-table-column
      >
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>

  <ConfirmationLinkDialog ref="issueDialog" />
  <ConfirmationPrintTemplates ref="printTemplates" kind="COMPLETION" @changed="loadPrintOptions" />
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

  <el-drawer v-model="detailVisible" title="完工证明" size="min(860px, 96vw)">
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
      <h3>客户确认</h3>
      <p v-if="detail.survey?.submittedAt"
        >已签字确认 · {{ formatDate(detail.survey.submittedAt)
        }}<span v-if="detail.survey.score != null">
          · 满意度 {{ detail.survey.score }} 分 ·
          {{ detail.survey.passed ? '达标' : '待跟进整改' }}</span
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
        v-if="detail.survey?.signatureImageDataUrl || detail.certificate.customerSignUrl"
        class="confirmation-signature"
        ><h3>客户手写签字</h3
        ><img
          :src="detail.survey?.signatureImageDataUrl || detail.certificate.customerSignUrl"
          alt="客户手写签字"
        /><el-button
          tag="a"
          :href="detail.survey?.signatureImageDataUrl || detail.certificate.customerSignUrl"
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
const handleAction = async (
  row: CompletionCertificateVO,
  action: 'issueConfirmation' | 'rejectCompletionCertificate' | 'archiveCompletionCertificate',
  actionText: string
) => {
  if (props.readonly || pendingActions.has(row.id!)) return
  const current = context
  await message.confirm(`确认${actionText}完工证明【${row.name}】？`)
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
const openPrint = async (row: CompletionCertificateVO) => {
  const current = context
  const data = await CompletionCertificateApi.getConfirmation(row.id!)
  if (current === context) await printDetail(data)
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
  margin-bottom: 20px;
}
.confirmation-heading h2 {
  margin: 0 0 6px;
  font-size: 16px;
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
</style>
