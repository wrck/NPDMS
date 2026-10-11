<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="项目" prop="projectId">
        <PmsEntitySelect
          v-model="query.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="projectName"
          value-field="id"
          query-field="projectName"
          placeholder="请选择项目"
          class="!w-220px"
        />
      </el-form-item>
      <el-form-item label="单号" prop="code">
        <el-input v-model="query.code" clearable class="!w-180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="类型" prop="procurementType">
        <el-select v-model="query.procurementType" clearable class="!w-140px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_EXT_PROC_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_APPROVAL_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="触发来源" prop="triggerSource">
        <el-select v-model="query.triggerSource" clearable class="!w-160px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_TRIGGER_SOURCE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openCreate()" v-hasPermi="['pms:imp-ext-proc:create']"
          ><Icon icon="ep:plus" />新建外采申请</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无外采申请数据">
      <el-table-column prop="code" label="单号" width="160" />
      <el-table-column prop="projectId" label="项目" min-width="180">
        <template #default="{ row }">
          <ProjectTag :project-id="row.projectId" />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="procurementType" label="类型" width="110">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_EXT_PROC_TYPE" :value="row.procurementType" />
        </template>
      </el-table-column>
      <el-table-column prop="productName" label="产品名称/物料名称" min-width="140" />
      <el-table-column prop="specification" label="规格型号" min-width="140" />
      <el-table-column label="数量" width="110">
        <template #default="{ row }">
          {{ row.quantity ?? '-' }} {{ row.unit || '' }}
        </template>
      </el-table-column>
      <el-table-column label="总价" width="130">
        <template #default="{ row }">
          {{ row.totalPrice ?? '-' }} {{ row.currency || '' }}
        </template>
      </el-table-column>
      <el-table-column prop="neededDate" label="需求日期" width="110" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_APPROVAL_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="applyTime" label="申请时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)" v-hasPermi="['pms:imp-ext-proc:query']"
            >明细</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0 || row.status === 4"
            @click="openEdit(row)"
            v-hasPermi="['pms:imp-ext-proc:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0 || row.status === 4"
            @click="handleSubmit(row)"
            v-hasPermi="['pms:imp-ext-proc:submit']"
            >提交</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 1 || row.status === 2"
            @click="openApprove(row)"
            v-hasPermi="['pms:imp-ext-proc:audit']"
            >审批</el-button
          >
          <el-button
            link
            type="info"
            v-if="row.status === 1 || row.status === 2"
            @click="handleWithdraw(row)"
            v-hasPermi="['pms:imp-ext-proc:submit']"
            >撤回</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status !== 3 && row.status !== 6"
            @click="handleTerminate(row)"
            v-hasPermi="['pms:imp-ext-proc:audit']"
            >终止</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 0 || row.status === 4"
            @click="remove(row)"
            v-hasPermi="['pms:imp-ext-proc:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 新建/编辑对话框 -->
  <ExternalProcurementFormDialog ref="formRef" v-model="formVisible" :form="form"
    :source-survey-id="sourceSurveyId" :saving="saving" @save="save" @attachment-changed="attachmentOwnerChanged" />

  <!-- 明细对话框 -->
  <ExternalProcurementDetailDialog v-model="detailVisible" :current="current" />

  <!-- 审批对话框 -->
  <Dialog v-model="approveVisible" title="审批外采申请" width="560px">
    <el-form ref="approveFormRef" :model="approveForm" :rules="approveRules" label-width="100px">
      <el-form-item label="审批动作" prop="approveAction">
        <el-radio-group v-model="approveForm.approveAction">
          <el-radio value="PASS">通过</el-radio>
          <el-radio value="REJECT">驳回</el-radio>
          <el-radio value="RETURN">退回修改</el-radio>
          <el-radio value="TRANSFER">转办</el-radio>
          <el-radio value="COUNTERSIGN">加签</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="审批人" prop="approverUserId">
        <PmsEntitySelect
          v-model="approveForm.approverUserId"
          :api="UserApi.getUserPage"
          label-field="nickname"
          value-field="id"
          query-field="nickname"
          placeholder="请选择审批人"
        />
      </el-form-item>
      <el-form-item label="审批意见" prop="approveOpinion">
        <el-input v-model="approveForm.approveOpinion" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="approveVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="confirmApprove">确认</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import ExternalProcurementFormDialog from './ExternalProcurementFormDialog.vue'
import ExternalProcurementDetailDialog from './ExternalProcurementDetailDialog.vue'
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { positiveShortcutId, surveyPath } from '@/views/pms/delivery-business/site-survey/siteSurveyOutsource'
import { loadSurveyActionContext } from '@/views/pms/delivery-business/site-survey/surveyActionContext'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as ExtProcApi from '@/api/pms/engineering/ext-proc'
import * as ProjectApi from '@/api/pms/project/projects'
import * as UserApi from '@/api/system/user'
import ProjectTag from '@/components/ProjectTag/index.vue'
import type { ExternalProcurementVO } from '@/api/pms/engineering/ext-proc'

defineOptions({ name: 'PmsEngExtProc' })
const message = useMessage()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const sourceSurveyId = ref<number>()
const loading = ref(false)
const saving = ref(false)
const rows = ref<ExternalProcurementVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  code: '',
  name: '',
  procurementType: '',
  status: undefined as number | undefined,
  triggerSource: ''
})

const load = async () => {
  loading.value = true
  try {
    const data = await ExtProcApi.getExternalProcurementPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 新建/编辑
const formVisible = ref(false)
const formRef = ref()
const form = reactive<ExternalProcurementVO>({
  projectId: undefined!,
  code: '',
  name: '',
  procurementType: 'GOODS',
  productName: '',
  productCode: '',
  specification: '',
  brand: '',
  productModel: '',
  quantity: undefined!,
  unit: '个',
  unitPrice: undefined,
  totalPrice: undefined,
  currency: 'CNY',
  supplierName: '',
  supplierContact: '',
  supplierPhone: '',
  neededDate: '',
  expectedDeliveryDate: '',
  attachmentFiles: '',
  triggerSource: 'MANUAL',
  applicantUserId: undefined!,
  applyTime: '',
  remark: ''
})

const openCreate = () => {
  sourceSurveyId.value = undefined
  form.version = undefined
  form.triggerRefId = undefined
  Object.assign(form, {
    id: undefined,
    projectId: undefined,
    code: '',
    name: '',
    procurementType: 'GOODS',
    productName: '',
    productCode: '',
    specification: '',
    brand: '',
    productModel: '',
    quantity: undefined,
    unit: '个',
    unitPrice: undefined,
    totalPrice: undefined,
    currency: 'CNY',
    supplierName: '',
    supplierContact: '',
    supplierPhone: '',
    neededDate: '',
    expectedDeliveryDate: '',
    attachmentFiles: '',
    triggerSource: 'MANUAL',
    applicantUserId: undefined,
    applyTime: '',
    remark: ''
  })
  formVisible.value = true
}
const openEdit = async (row: ExternalProcurementVO) => {
  const detail = await ExtProcApi.getExternalProcurement(row.id!)
  Object.assign(form, detail)
  formVisible.value = true
}
const attachmentOwnerChanged = (saved: { version?: number; status?: number; configLogUrl?: string }) => {
  form.version = saved.version
  form.status = saved.status
}
const save = async () => {
  if (saving.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await ExtProcApi.updateExternalProcurement(form)
      message.success('更新成功')
    } else {
      await ExtProcApi.createExternalProcurement(form)
      message.success('创建成功')
    }
    formVisible.value = false
    if (sourceSurveyId.value) await router.push({ path: surveyPath, query: { surveyId: String(sourceSurveyId.value) } })
    else await load()
  } finally {
    saving.value = false
  }
}

// 明细
const detailVisible = ref(false)
const current = ref<Partial<ExternalProcurementVO>>({})
const openDetail = async (row: ExternalProcurementVO) => {
  current.value = await ExtProcApi.getExternalProcurement(row.id!)
  detailVisible.value = true
}

// 审批
const approveVisible = ref(false)
const approveFormRef = ref()
const approveForm = reactive({
  id: undefined as number | undefined,
  approveAction: 'PASS',
  approveOpinion: '',
  approverUserId: undefined as number | undefined
})
const approveRules = {
  approveAction: [{ required: true, message: '请选择审批动作' }],
  approverUserId: [{ required: true, message: '请选择审批人' }]
}
const openApprove = (row: ExternalProcurementVO) => {
  Object.assign(approveForm, {
    id: row.id,
    approveAction: 'PASS',
    approveOpinion: '',
    approverUserId: undefined
  })
  approveVisible.value = true
}
const confirmApprove = async () => {
  await approveFormRef.value.validate()
  saving.value = true
  try {
    await ExtProcApi.approveExternalProcurement(approveForm as any)
    message.success('审批完成')
    approveVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// 状态操作
const handleSubmit = async (row: ExternalProcurementVO) => {
  await message.confirm('确认提交此外采申请？提交后将进入审批流程。')
  await ExtProcApi.submitExternalProcurement(row.id!)
  message.success('提交成功')
  await load()
}
const handleWithdraw = async (row: ExternalProcurementVO) => {
  await message.confirm('确认撤回此外采申请？')
  await ExtProcApi.withdrawExternalProcurement(row.id!)
  message.success('撤回成功')
  await load()
}
const handleTerminate = async (row: ExternalProcurementVO) => {
  await message.confirm('确认终止此外采申请？终止后不可恢复。')
  await ExtProcApi.terminateExternalProcurement(row.id!)
  message.success('终止成功')
  await load()
}
const remove = async (row: ExternalProcurementVO) => {
  await message.delConfirm()
  await ExtProcApi.deleteExternalProcurement(row.id!)
  message.success('删除成功')
  await load()
}

onMounted(load)
watch(() => [route.query.surveyId, route.query.deviceSn], async ([value, sn]) => {
  if (!value) return
  const surveyId = positiveShortcutId(value)
  if (!surveyId) { message.warning('工勘来源编号无效'); return }
  try {
    const context = await loadSurveyActionContext(surveyId, 'procurement', typeof sn === 'string' ? sn : undefined)
    openCreate()
    sourceSurveyId.value = surveyId
    Object.assign(form, context, { applicantUserId: userStore.getUser.id, applyTime: Date.now() })
  } catch(error) { message.warning(error instanceof Error ? error.message : '工勘来源读取失败') }
}, { immediate: true })
</script>
