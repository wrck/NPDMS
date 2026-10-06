<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
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
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_COMPLETION_CERT_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:acc-completion-certificate:create']"
          ><Icon icon="ep:plus" />新增完工证明</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="编号" min-width="140" />
      <el-table-column prop="customerId" label="客户编号" width="100" />
      <el-table-column prop="completionDate" label="完工时间" width="120" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_COMPLETION_CERT_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="520" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:acc-completion-certificate:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'submitCompletionCertificate', '提交')"
            v-hasPermi="['pms:acc-completion-certificate:submit']"
            >提交</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 1"
            @click="handleAction(row, 'customerConfirmCompletionCertificate', '客户确认')"
            v-hasPermi="['pms:acc-completion-certificate:audit']"
            >客户确认</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 1"
            @click="handleAction(row, 'rejectCompletionCertificate', '驳回')"
            v-hasPermi="['pms:acc-completion-certificate:audit']"
            >驳回</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 2"
            @click="handleAction(row, 'archiveCompletionCertificate', '归档')"
            v-hasPermi="['pms:acc-completion-certificate:audit']"
            >归档</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:acc-completion-certificate:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>

  <Dialog v-model="formVisible" :title="form.id ? '编辑完工证明' : '新增完工证明'" width="780px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-row :gutter="16">
        <el-col v-if="!props.projectId" :span="12">
          <el-form-item label="项目" prop="projectId">
            <PmsEntitySelect
              v-model="form.projectId"
              :api="ProjectApi.getProjectPage"
              label-field="projectName"
              value-field="id"
              query-field="projectName"
              placeholder="请选择项目"
              :disabled="!!form.id"
              @change="onProjectChange"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="编号" prop="code">
            <el-input v-model="form.code" disabled placeholder="保存后按统一规则自动生成" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="客户单位" prop="customerUnit">
            <el-input v-model="form.customerUnit" placeholder="选择项目后自动带入，可修改" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="合同号" prop="contractNo">
            <el-input v-model="form.contractNo" placeholder="选择项目后自动带入，可修改" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="工程服务类型" prop="serviceType">
            <el-radio-group v-model="form.serviceType">
              <el-radio value="工程实施">工程实施</el-radio>
              <el-radio value="工程督导">工程督导</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="完工时间" prop="completionDate">
            <el-date-picker v-model="form.completionDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="迪普工程师" prop="engineerName">
            <PmsEntitySelect
              v-model="form.engineerName"
              :api="UserApi.getUserPage"
              label-field="nickname"
              value-field="nickname"
              query-field="nickname"
              placeholder="默认当前用户，可修改"
              @change="onEngineerChange"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="联系方式" prop="engineerContact">
            <el-input v-model="form.engineerContact" placeholder="选择工程师后自动带入，可修改" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="工程服务内容" prop="itemArrival">
            <div class="cert-service-items">
              <div v-for="item in certServiceItems" :key="item.field" class="cert-service-row">
                <span class="cert-service-label">{{ item.label }}</span>
                <el-radio-group v-model="form[item.field]">
                  <el-radio value="是">是</el-radio>
                  <el-radio value="否">否</el-radio>
                  <el-radio value="不涉及">不涉及</el-radio>
                </el-radio-group>
              </div>
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="设备类型和数量" prop="devices">
            <div class="device-table">
              <div class="device-row device-header">
                <span>设备类型</span>
                <span>设备型号</span>
                <span>数量</span>
                <span></span>
              </div>
              <div v-for="(device, index) in deviceRows" :key="index" class="device-row">
                <el-input v-model="device.deviceType" placeholder="设备类型" />
                <el-input v-model="device.deviceModel" placeholder="设备型号" />
                <el-input-number v-model="device.quantity" :min="1" controls-position="right" class="!w-full" />
                <el-button link type="danger" @click="deviceRows.splice(index, 1)">删除</el-button>
              </div>
              <el-button link type="primary" @click="addDeviceRow">
                <Icon icon="ep:plus" />添加一行
              </el-button>
              <div class="device-tip">选择项目后按项目设备明细自动填充，可调整</div>
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="甲方签章" prop="customerSignUrl">
            <div class="sign-block">
              <div class="sign-pad-area">
                <SignaturePad v-model="customerSignDataUrl" :form-create-inject="signatureInject" />
              </div>
              <el-date-picker
                v-model="form.customerSignDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="签章日期"
                class="!w-160px"
              />
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="服务方签章" prop="vendorSignUrl">
            <div class="sign-block">
              <div class="sign-pad-area">
                <SignaturePad v-model="vendorSignDataUrl" :form-create-inject="signatureInject" />
              </div>
              <el-date-picker
                v-model="form.vendorSignDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="签章日期"
                class="!w-160px"
              />
            </div>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <DeliveryPanel v-if="form.id" :key="form.id" owner-module="ACC" entity-type="completionCertificate"
      :entity-id="form.id" :type-codes="['COMPLETION_CERTIFICATE']" readonly />
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import DeliveryPanel from '@/components/BusinessEntity/DeliveryPanel.vue'
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import { useUserStore } from '@/store/modules/user'
import * as CompletionCertificateApi from '@/api/pms/acceptance/completion-certificate'
import * as ProjectApi from '@/api/pms/project/projects'
import * as UserApi from '@/api/system/user'
import * as DeviceApi from '@/api/pms/asset/device'
import * as FileApi from '@/api/infra/file'
import { SignaturePad } from '@/components/FormCreate'
import { signaturePngFile } from '@/components/FormCreate/src/customerConfirmation'
import type { CompletionCertificateVO } from '@/api/pms/acceptance/completion-certificate'

defineOptions({ name: 'PmsCompletionCertificate' })
const props = defineProps<{ projectId?: number }>()
const message = useMessage()
const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<CompletionCertificateVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: props.projectId as number | undefined,
  status: undefined
})
const formVisible = ref(false)
const formRef = ref()
// 签章复用培训客户确认/满意度问卷的 form-create signaturePad 手写控件：
// 控件值为 PNG dataURL，保存时经 signaturePngFile 转 File 走既有文件上传链路，实体列落文件 URL。
const customerSignDataUrl = ref('')
const vendorSignDataUrl = ref('')
const signatureInject = {
  t: (key: string) =>
    (
      {
        signaturePadTip: '点击添加手写签章',
        signaturePadTitle: '请在虚线框内书写',
        reset: '重置',
        ok: '确定'
      } as Record<string, string>
    )[key] ?? key
}
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
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  serviceType: [{ required: true, message: '请选择工程服务类型' }]
}
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
  loading.value = true
  try {
    const data = await CompletionCertificateApi.getCompletionCertificatePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
// 按项目设备明细聚合成子表行：产品名+型号相同的设备计数为数量
const fillDevicesFromProject = async (projectId: number) => {
  const data = await DeviceApi.getDevicePage({ projectId, pageNo: 1, pageSize: 100 })
  const grouped = new Map<string, CertDeviceRow>()
  for (const device of data.list ?? []) {
    const type = device.productName ?? ''
    const model = device.productModel ?? ''
    const key = `${type}|${model}`
    const row = grouped.get(key) ?? { deviceType: type, deviceModel: model, quantity: 0 }
    row.quantity += 1
    grouped.set(key, row)
  }
  deviceRows.value = grouped.size
    ? [...grouped.values()].sort(
        (a, b) => a.deviceType.localeCompare(b.deviceType) || a.deviceModel.localeCompare(b.deviceModel)
      )
    : [{ deviceType: '', deviceModel: '', quantity: 1 }]
}
const resolveProject = async (projectId?: number) => {
  if (!projectId) return
  const project = await ProjectApi.getProject(projectId)
  projectNameCache.value = project.projectName ?? ''
  form.customerUnit = form.customerUnit || project.customerName || ''
  if (!form.contractNo) {
    form.contractNo = project.contractNo ?? ''
    if (!form.contractNo) {
      // 项目未挂合同时按设备明细的合同号兜底
      const data = await DeviceApi.getDevicePage({ projectId, pageNo: 1, pageSize: 100 })
      const contractNos = new Set((data.list ?? []).map((d) => d.contractNo).filter(Boolean))
      if (contractNos.size === 1) form.contractNo = [...contractNos][0]
    }
  }
  if (!deviceRows.value.length) await fillDevicesFromProject(projectId)
}
const prefillCurrentEngineer = async () => {
  try {
    const data = await UserApi.getUserPage({ pageNo: 1, pageSize: 100 })
    const current = data.list?.find((user: any) => user.id === userStore.user.id)
    if (current) {
      form.engineerName = current.nickname
      form.engineerUserId = current.id
      form.engineerContact = current.mobile ?? ''
    }
  } catch {
    // 用户分页不可见时留给用户手填
  }
}
const onProjectChange = (projectId?: number) => {
  projectNameCache.value = ''
  form.customerUnit = ''
  form.contractNo = ''
  deviceRows.value = []
  resolveProject(projectId)
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
  // 重置为全新表单基准，再叠加编辑数据
  Object.assign(
    form,
    {
      id: undefined,
      projectId: (props.projectId ?? undefined) as number,
      name: '',
      code: '',
      customerId: undefined,
      completionDate: '',
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
      version: undefined
    },
    row || {}
  )
  customerSignDataUrl.value = ''
  vendorSignDataUrl.value = ''
  projectNameCache.value = ''
  deviceRows.value = []
  formVisible.value = true
  if (form.id) {
    // 设备明细子表随详情返回，编辑时拉全量数据再套旧信封兼容
    const detail = await CompletionCertificateApi.getCompletionCertificate(form.id)
    Object.assign(form, detail)
    deviceRows.value = (detail.devices ?? []).map((d) => ({
      deviceType: d.deviceType ?? '',
      deviceModel: d.deviceModel ?? '',
      quantity: d.quantity ?? 1
    }))
    // 签章控件值为 dataURL/文件 URL，编辑时从实体列回种，未重新签名则原样保留
    customerSignDataUrl.value = form.customerSignUrl ?? ''
    vendorSignDataUrl.value = form.vendorSignUrl ?? ''
    applyLegacyEnvelope()
    if (form.projectId) resolveProject(form.projectId)
  } else {
    prefillCurrentEngineer()
    if (props.projectId) resolveProject(props.projectId)
  }
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    // 证明名称即项目名称，随项目带入，不再单独填写
    if (!form.name && (projectNameCache.value || form.projectId)) {
      if (!projectNameCache.value && form.projectId) {
        projectNameCache.value = (await ProjectApi.getProject(form.projectId)).projectName ?? ''
      }
      form.name = projectNameCache.value
    }
    form.devices = deviceRows.value
    form.customerSignUrl = await uploadSignature(customerSignDataUrl.value)
    form.vendorSignUrl = await uploadSignature(vendorSignDataUrl.value)
    form.id
      ? await CompletionCertificateApi.updateCompletionCertificate(form)
      : await CompletionCertificateApi.createCompletionCertificate(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: CompletionCertificateVO) => {
  await message.delConfirm()
  await CompletionCertificateApi.deleteCompletionCertificate(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (
  row: CompletionCertificateVO,
  action:
    | 'submitCompletionCertificate'
    | 'customerConfirmCompletionCertificate'
    | 'rejectCompletionCertificate'
    | 'archiveCompletionCertificate',
  actionText: string
) => {
  await message.confirm(`确认${actionText}完工证明【${row.code || row.name}】？`)
  await (CompletionCertificateApi as any)[action](row.id!)
  message.success(`${actionText}成功`)
  await load()
}

onMounted(load)
</script>

<style lang="scss" scoped>
.cert-service-items {
  width: 100%;
}

.cert-service-row {
  display: flex;
  gap: 16px;
  align-items: center;
  width: 100%;
  margin-bottom: 4px;

  .cert-service-label {
    flex: 1;
    min-width: 0;
    font-size: 13px;
    color: var(--el-text-color-regular);
  }
}

.device-table {
  width: 100%;

  .device-row {
    display: grid;
    grid-template-columns: 1fr 1fr 120px 48px;
    gap: 8px;
    align-items: center;
    margin-bottom: 6px;

    &.device-header {
      margin-bottom: 2px;
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
  }

  .device-tip {
    font-size: 12px;
    color: var(--el-text-color-placeholder);
  }
}

.sign-block {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  width: 100%;

  .sign-pad-area {
    flex: 1;
    min-width: 0;
  }
}
</style>
