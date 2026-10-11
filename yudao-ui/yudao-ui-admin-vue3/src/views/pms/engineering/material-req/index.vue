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
      <el-form-item label="类型" prop="requisitionType">
        <el-select v-model="query.requisitionType" clearable class="!w-140px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_MATERIAL_REQ_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="库存状态" prop="stockStatus">
        <el-select v-model="query.stockStatus" clearable class="!w-140px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_STOCK_STATUS)"
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
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openCreate()" v-hasPermi="['pms:imp-material-req:create']"
          ><Icon icon="ep:plus" />新建领料申请</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无领料申请数据">
      <el-table-column prop="code" label="单号" width="160" />
      <el-table-column prop="projectId" label="项目" min-width="180">
        <template #default="{ row }">
          <ProjectTag :project-id="row.projectId" />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="requisitionType" label="类型" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_MATERIAL_REQ_TYPE" :value="row.requisitionType" />
        </template>
      </el-table-column>
      <el-table-column prop="productCode" label="产品编码/物料编码" width="140" />
      <el-table-column prop="productName" label="产品名称/物料名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="specification" label="规格" width="120" show-overflow-tooltip />
      <el-table-column label="数量" width="110">
        <template #default="{ row }">
          {{ row.quantity ?? '-' }} {{ row.unit || '' }}
        </template>
      </el-table-column>
      <el-table-column prop="neededDate" label="需求日期" width="110" />
      <el-table-column prop="stockStatus" label="库存状态" width="110">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_STOCK_STATUS" :value="row.stockStatus" />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_APPROVAL_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="applyTime" label="申请时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)" v-hasPermi="['pms:imp-material-req:query']"
            >明细</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0 || row.status === 4"
            @click="openEdit(row)"
            v-hasPermi="['pms:imp-material-req:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0 || row.status === 4"
            @click="handleSubmit(row)"
            v-hasPermi="['pms:imp-material-req:submit']"
            >提交</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 1 || row.status === 2"
            @click="openApprove(row)"
            v-hasPermi="['pms:imp-material-req:audit']"
            >审批</el-button
          >
          <el-button
            link
            type="info"
            v-if="row.status === 1 || row.status === 2"
            @click="handleWithdraw(row)"
            v-hasPermi="['pms:imp-material-req:submit']"
            >撤回</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status !== 3 && row.status !== 6"
            @click="handleTerminate(row)"
            v-hasPermi="['pms:imp-material-req:audit']"
            >终止</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 0 || row.status === 4"
            @click="remove(row)"
            v-hasPermi="['pms:imp-material-req:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 新建/编辑对话框 -->
  <MaterialRequisitionFormDialog ref="formRef" v-model="formVisible" :form="form"
    :source-survey-id="sourceSurveyId" :saving="saving" @save="save" />

  <!-- 明细对话框 -->
  <MaterialRequisitionDetailDialog v-model="detailVisible" :current="current" />

  <!-- 审批对话框 -->
  <Dialog v-model="approveVisible" title="审批领料申请" width="560px">
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
import MaterialRequisitionFormDialog from './MaterialRequisitionFormDialog.vue'
import MaterialRequisitionDetailDialog from './MaterialRequisitionDetailDialog.vue'
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { positiveShortcutId, surveyPath } from '@/views/pms/delivery-business/site-survey/siteSurveyOutsource'
import { loadSurveyActionContext } from '@/views/pms/delivery-business/site-survey/surveyActionContext'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as MaterialReqApi from '@/api/pms/engineering/material-req'
import * as ProjectApi from '@/api/pms/project/projects'
import * as UserApi from '@/api/system/user'
import type { MaterialRequisitionVO } from '@/api/pms/engineering/material-req'
import ProjectTag from '@/components/ProjectTag/index.vue'

defineOptions({ name: 'PmsEngMaterialReq' })
const message = useMessage()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const sourceSurveyId = ref<number>()
const loading = ref(false)
const saving = ref(false)
const rows = ref<MaterialRequisitionVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  code: '',
  name: '',
  requisitionType: '',
  stockStatus: '',
  status: undefined as number | undefined
})

const load = async () => {
  loading.value = true
  try {
    const data = await MaterialReqApi.getMaterialRequisitionPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 新建/编辑
const formVisible = ref(false)
const formRef = ref()
const form = reactive<MaterialRequisitionVO>({
  projectId: undefined!,
  code: '',
  name: '',
  requisitionType: 'SPARE',
  equipmentId: undefined,
  productName: '',
  productCode: '',
  specification: '',
  quantity: undefined!,
  unit: '个',
  neededDate: '',
  warehouseId: undefined,
  warehouseName: '',
  stockStatus: 'IN_STOCK',
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
    requisitionType: 'SPARE',
    equipmentId: undefined,
    productName: '',
    productCode: '',
    specification: '',
    quantity: undefined,
    unit: '个',
    neededDate: '',
    warehouseId: undefined,
    warehouseName: '',
    stockStatus: 'IN_STOCK',
    attachmentFiles: '',
    triggerSource: 'MANUAL',
    applicantUserId: undefined,
    applyTime: '',
    remark: ''
  })
  formVisible.value = true
}
const openEdit = async (row: MaterialRequisitionVO) => {
  const detail = await MaterialReqApi.getMaterialRequisition(row.id!)
  Object.assign(form, detail)
  formVisible.value = true
}
const save = async () => {
  if (saving.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await MaterialReqApi.updateMaterialRequisition(form)
      message.success('更新成功')
    } else {
      await MaterialReqApi.createMaterialRequisition(form)
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
const current = ref<Partial<MaterialRequisitionVO>>({})
const openDetail = async (row: MaterialRequisitionVO) => {
  current.value = await MaterialReqApi.getMaterialRequisition(row.id!)
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
const openApprove = (row: MaterialRequisitionVO) => {
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
    await MaterialReqApi.approveMaterialRequisition(approveForm as any)
    message.success('审批完成')
    approveVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// 状态操作
const handleSubmit = async (row: MaterialRequisitionVO) => {
  await message.confirm('确认提交此领料申请？提交后将进入审批流程。')
  await MaterialReqApi.submitMaterialRequisition(row.id!)
  message.success('提交成功')
  await load()
}
const handleWithdraw = async (row: MaterialRequisitionVO) => {
  await message.confirm('确认撤回此领料申请？')
  await MaterialReqApi.withdrawMaterialRequisition(row.id!)
  message.success('撤回成功')
  await load()
}
const handleTerminate = async (row: MaterialRequisitionVO) => {
  await message.confirm('确认终止此领料申请？终止后不可恢复。')
  await MaterialReqApi.terminateMaterialRequisition(row.id!)
  message.success('终止成功')
  await load()
}
const remove = async (row: MaterialRequisitionVO) => {
  await message.delConfirm()
  await MaterialReqApi.deleteMaterialRequisition(row.id!)
  message.success('删除成功')
  await load()
}

onMounted(load)
watch(() => [route.query.surveyId, route.query.deviceSn], async ([value, sn]) => {
  if (!value) return
  const surveyId = positiveShortcutId(value)
  if (!surveyId) { message.warning('工勘来源编号无效'); return }
  try {
    const context = await loadSurveyActionContext(surveyId, 'material', typeof sn === 'string' ? sn : undefined)
    openCreate()
    sourceSurveyId.value = surveyId
    Object.assign(form, context, { applicantUserId: userStore.getUser.id, applyTime: Date.now() })
  } catch(error) { message.warning(error instanceof Error ? error.message : '工勘来源读取失败') }
}, { immediate: true })
</script>
