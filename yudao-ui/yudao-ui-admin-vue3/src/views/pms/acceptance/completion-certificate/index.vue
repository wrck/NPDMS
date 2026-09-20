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
      <el-form-item label="证明名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
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
      <el-table-column prop="name" label="证明名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="customerId" label="客户编号" width="100" />
      <el-table-column prop="certificateNo" label="证书编号" min-width="140" />
      <el-table-column prop="signedDate" label="签署日期" width="120" />
      <el-table-column prop="satisfactionScore" label="满意度" width="90">
        <template #default="{ row }">
          <el-rate v-model="row.satisfactionScore" disabled size="small" />
        </template>
      </el-table-column>
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
            v-hasPermi="['pms:acc-completion-certificate:update']"
            >提交</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 1"
            @click="handleAction(row, 'customerConfirmCompletionCertificate', '客户确认')"
            v-hasPermi="['pms:acc-completion-certificate:update']"
            >客户确认</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 1"
            @click="handleAction(row, 'rejectCompletionCertificate', '驳回')"
            v-hasPermi="['pms:acc-completion-certificate:update']"
            >驳回</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 2"
            @click="handleAction(row, 'archiveCompletionCertificate', '归档')"
            v-hasPermi="['pms:acc-completion-certificate:update']"
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
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="证明名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col v-if="!props.projectId" :span="12">
          <el-form-item label="客户" prop="customerId">
            <PmsEntitySelect
              v-model="form.customerId"
              :api="CustomerApi.getCustomerPage"
              :label-field="['code', 'name']"
              value-field="id"
              query-field="name"
              placeholder="请选择客户"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="证书编号" prop="certificateNo"><el-input v-model="form.certificateNo" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="签署日期" prop="signedDate">
            <el-date-picker v-model="form.signedDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="工程服务类型" prop="certServiceType">
            <el-radio-group v-model="form.certServiceType">
              <el-radio value="工程实施">工程实施</el-radio>
              <el-radio value="工程督导">工程督导</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="迪普工程师" prop="certEngineer">
            <el-input v-model="form.certEngineer" placeholder="填表人带入，可修改" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="联系方式" prop="certContact">
            <el-input v-model="form.certContact" placeholder="项目干系人信息带入，可修改" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="满意度评分" prop="satisfactionScore">
            <el-rate v-model="form.satisfactionScore" :max="5" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="工程服务内容" prop="certItems">
            <div class="cert-service-items">
              <div v-for="item in certServiceItems" :key="item.key" class="cert-service-row">
                <span class="cert-service-label">{{ item.label }}</span>
                <el-radio-group v-model="form.certItems![item.key]">
                  <el-radio value="是">是</el-radio>
                  <el-radio value="否">否</el-radio>
                  <el-radio value="不涉及">不涉及</el-radio>
                </el-radio-group>
              </div>
              <el-input
                v-model="form.certItems!.deviceSummary"
                type="textarea"
                :rows="2"
                placeholder="⑥ 设备类型和数量：填写本次工程涉及的设备类型和数量"
              />
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="客户意见" prop="customerOpinion">
            <Editor v-model="form.customerOpinion" :height="300" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="签章附件" prop="signatureUrl">
            <UploadImg v-model="form.signatureUrl" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="甲方签章日期" prop="certCustomerSignDate">
            <el-date-picker v-model="form.certCustomerSignDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="服务方签章" prop="certVendorSignUrl">
            <UploadImg v-model="form.certVendorSignUrl" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="服务方签章日期" prop="certVendorSignDate">
            <el-date-picker v-model="form.certVendorSignDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="证明附件" prop="attachmentUrl">
            <UploadFile v-model="form.attachmentUrl!" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input v-model="form.remark" type="textarea" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import * as CompletionCertificateApi from '@/api/pms/acceptance/completion-certificate'
import * as ProjectApi from '@/api/pms/project/projects'
import * as CustomerApi from '@/api/pms/customer'
import type { CompletionCertificateVO } from '@/api/pms/acceptance/completion-certificate'

defineOptions({ name: 'PmsCompletionCertificate' })
const props = defineProps<{ projectId?: number }>()
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<CompletionCertificateVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: props.projectId as number | undefined,
  name: '',
  status: undefined
})
const formVisible = ref(false)
const formRef = ref()
// Demo 6.2 完工证明补充字段：随记录 remark 以 JSON 信封保存（既有备注文本降级为 note 键保留），
// 既有 VO 列（名称/编号/日期/签章图/客户意见等）绑定不变；后续拆列仅需调整本序列化边界。
interface CompletionCertExtra {
  serviceType: string
  engineer: string
  contact: string
  items: Record<string, string>
  customerSignDate: string
  vendorSignUrl: string
  vendorSignDate: string
  note: string
}
const certServiceItems = [
  { key: 'i1', label: '① 完成到货验收' },
  { key: 'i2', label: '② 完成设备硬件安装和软件调测' },
  { key: 'i3', label: '③ 完成业务上线/割接且业务测试正常' },
  { key: 'i4', label: '④ 完成产品维护现场讲解和培训' },
  { key: 'i5', label: '⑤ 工程文档、帐号密码已移交并协助修改' }
] as const
const emptyCertItems = (): Record<string, string> => ({ i1: '', i2: '', i3: '', i4: '', i5: '', deviceSummary: '' })
const form = reactive<CompletionCertificateVO & { certServiceType?: string; certEngineer?: string; certContact?: string; certItems?: Record<string, string>; certCustomerSignDate?: string; certVendorSignUrl?: string; certVendorSignDate?: string }>({
  projectId: props.projectId as number,
  name: '',
  certItems: emptyCertItems()
})
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  name: [{ required: true, message: '请输入证明名称' }],
  certServiceType: [{ required: true, message: '请选择工程服务类型' }]
}
const parseCertRemark = (raw: string | undefined | null): Partial<CompletionCertExtra> => {
  try {
    const v = raw ? JSON.parse(raw) : null
    return v && typeof v === 'object' && !Array.isArray(v) ? v : {}
  } catch {
    // 历史纯文本备注降级为 note
    return { note: raw || '' }
  }
}
const syncCertExtra = (raw: string | undefined | null) => {
  const parsed = parseCertRemark(raw)
  form.certServiceType = String(parsed['serviceType'] ?? '')
  form.certEngineer = String(parsed['engineer'] ?? '')
  form.certContact = String(parsed['contact'] ?? '')
  form.certItems = { ...emptyCertItems(), ...((parsed['items'] as Record<string, string>) ?? {}) }
  form.certCustomerSignDate = String(parsed['customerSignDate'] ?? '')
  form.certVendorSignUrl = String(parsed['vendorSignUrl'] ?? '')
  form.certVendorSignDate = String(parsed['vendorSignDate'] ?? '')
  form.remark = String(parsed['note'] ?? '')
}
const writeCertExtra = () => {
  form.remark = JSON.stringify({
    serviceType: form.certServiceType ?? '',
    engineer: form.certEngineer ?? '',
    contact: form.certContact ?? '',
    items: form.certItems ?? emptyCertItems(),
    customerSignDate: form.certCustomerSignDate ?? '',
    vendorSignUrl: form.certVendorSignUrl ?? '',
    vendorSignDate: form.certVendorSignDate ?? '',
    note: form.remark ?? ''
  })
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
const openForm = (row?: CompletionCertificateVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: (props.projectId ?? undefined) as number,
      name: '',
      customerId: undefined,
      certificateNo: '',
      signedDate: '',
      satisfactionScore: 5,
      customerOpinion: '',
      signatureUrl: '',
      attachmentUrl: '',
      status: 0,
      remark: '',
      version: undefined
    },
    row || {}
  )
  syncCertExtra(row?.remark ?? form.remark ?? '')
  formVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    writeCertExtra()
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
  await message.confirm(`确认${actionText}完工证明【${row.name}】？`)
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
</style>
