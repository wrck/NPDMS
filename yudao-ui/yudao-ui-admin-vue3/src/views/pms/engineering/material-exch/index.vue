<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="项目" prop="projectId">
        <PmsEntitySelect
          v-model="query.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="name"
          value-field="id"
          query-field="name"
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
      <el-form-item label="换货类型" prop="exchangeType">
        <el-select v-model="query.exchangeType" clearable class="!w-140px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_MATERIAL_EXCH_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="CRM推送状态" prop="crmPushStatus">
        <el-select v-model="query.crmPushStatus" clearable class="!w-140px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_CRM_SYNC_STATUS)"
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
        <el-button type="primary" @click="openCreate()" v-hasPermi="['pms:eng-material-exch:create']"
          ><Icon icon="ep:plus" />新建换货申请</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无物料换货数据">
      <el-table-column prop="code" label="单号" width="160" />
      <el-table-column prop="projectId" label="项目" min-width="180">
        <template #default="{ row }">
          <ProjectTag :project-id="row.projectId" />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="exchangeType" label="换货类型" width="110">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_MATERIAL_EXCH_TYPE" :value="row.exchangeType" />
        </template>
      </el-table-column>
      <el-table-column prop="materialName" label="物料名称" min-width="140" />
      <el-table-column label="数量" width="100">
        <template #default="{ row }">
          {{ row.quantity ?? '-' }} {{ row.unit || '' }}
        </template>
      </el-table-column>
      <el-table-column prop="originalOrderNo" label="原订单号" width="140" />
      <el-table-column prop="crmPushStatus" label="CRM状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_CRM_SYNC_STATUS" :value="row.crmPushStatus" />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_APPROVAL_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="applyTime" label="申请时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="420" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)" v-hasPermi="['pms:eng-material-exch:query']"
            >明细</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0 || row.status === 4"
            @click="openEdit(row)"
            v-hasPermi="['pms:eng-material-exch:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0 || row.status === 4"
            @click="handleSubmit(row)"
            v-hasPermi="['pms:eng-material-exch:submit']"
            >提交</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 1 || row.status === 2"
            @click="openApprove(row)"
            v-hasPermi="['pms:eng-material-exch:audit']"
            >审批</el-button
          >
          <el-button
            link
            type="info"
            v-if="row.status === 1 || row.status === 2"
            @click="handleWithdraw(row)"
            v-hasPermi="['pms:eng-material-exch:submit']"
            >撤回</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.crmPushStatus === 'PENDING' && row.status === 3"
            @click="handlePushCrm(row)"
            v-hasPermi="['pms:eng-material-exch:push-crm']"
            >推送CRM</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status !== 3 && row.status !== 6"
            @click="handleTerminate(row)"
            v-hasPermi="['pms:eng-material-exch:audit']"
            >终止</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 0 || row.status === 4"
            @click="remove(row)"
            v-hasPermi="['pms:eng-material-exch:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 新建/编辑对话框 -->
  <Dialog v-model="formVisible" :title="form.id ? '编辑换货申请' : '新建换货申请'" width="960px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="项目" prop="projectId">
            <PmsEntitySelect
              v-model="form.projectId"
              :api="ProjectApi.getProjectPage"
              label-field="name"
              value-field="id"
              query-field="name"
              placeholder="请选择项目"
              :disabled="!!form.id"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="单号" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 ME-2026-001" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="换货类型" prop="exchangeType">
            <el-select v-model="form.exchangeType" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_MATERIAL_EXCH_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="关联设备" prop="equipmentId">
            <PmsEntitySelect
              v-model="form.equipmentId"
              :api="EquipmentApi.getEquipmentPage"
              :label-field="['serialNumber', 'name']"
              value-field="id"
              query-field="serialNumber"
              placeholder="请选择关联设备"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="物料名称" prop="materialName">
            <el-input v-model="form.materialName" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="物料编码" prop="materialCode">
            <el-input v-model="form.materialCode" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="规格型号" prop="specification">
            <el-input v-model="form.specification" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="数量" prop="quantity">
            <el-input-number v-model="form.quantity" :min="0" :precision="2" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="单位" prop="unit">
            <el-input v-model="form.unit" placeholder="如 个/台/套" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="原订单号" prop="originalOrderNo">
            <el-input v-model="form.originalOrderNo" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="申请人" prop="applicantUserId">
            <PmsEntitySelect
              v-model="form.applicantUserId"
              :api="UserApi.getUserPage"
              label-field="nickname"
              value-field="id"
              query-field="nickname"
              placeholder="请选择申请人"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="申请时间" prop="applyTime">
            <el-date-picker
              v-model="form.applyTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择申请时间"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="换货原因" prop="reason">
            <Editor v-model="form.reason" height="180px" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="原因附件" prop="reasonFiles">
            <UploadFile v-model="form.reasonFiles" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input v-model="form.remark" type="textarea" :rows="2" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>

  <!-- 明细对话框 -->
  <Dialog v-model="detailVisible" title="换货申请明细" width="960px">
    <el-descriptions :column="2" border class="mb-15px">
      <el-descriptions-item label="单号">{{ current.code }}</el-descriptions-item>
      <el-descriptions-item label="名称">{{ current.name }}</el-descriptions-item>
      <el-descriptions-item label="项目"><ProjectTag :project-id="current.projectId" /></el-descriptions-item>
      <el-descriptions-item label="换货类型">
        <dict-tag :type="DICT_TYPE.PMS_MATERIAL_EXCH_TYPE" :value="current.exchangeType" />
      </el-descriptions-item>
      <el-descriptions-item label="关联设备"><EquipmentTag :equipment-id="current.equipmentId" /></el-descriptions-item>
      <el-descriptions-item label="物料名称">{{ current.materialName }}</el-descriptions-item>
      <el-descriptions-item label="物料编码">{{ current.materialCode }}</el-descriptions-item>
      <el-descriptions-item label="规格型号">{{ current.specification }}</el-descriptions-item>
      <el-descriptions-item label="数量">{{ current.quantity }} {{ current.unit }}</el-descriptions-item>
      <el-descriptions-item label="原订单号">{{ current.originalOrderNo }}</el-descriptions-item>
      <el-descriptions-item label="申请人">{{ current.applicantUserId }}</el-descriptions-item>
      <el-descriptions-item label="申请时间">{{ current.applyTime }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <dict-tag :type="DICT_TYPE.PMS_APPROVAL_STATUS" :value="current.status" />
      </el-descriptions-item>
      <el-descriptions-item label="CRM推送状态">
        <dict-tag :type="DICT_TYPE.PMS_CRM_SYNC_STATUS" :value="current.crmPushStatus" />
      </el-descriptions-item>
      <el-descriptions-item label="CRM推送时间">{{ current.crmPushTime }}</el-descriptions-item>
      <el-descriptions-item label="CRM订单号">{{ current.crmOrderNo }}</el-descriptions-item>
      <el-descriptions-item label="换货原因" :span="2">
        <div v-html="current.reason"></div>
      </el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ current.remark }}</el-descriptions-item>
      <el-descriptions-item v-if="current.approveOpinion" label="审批意见" :span="2">
        {{ current.approveOpinion }}
      </el-descriptions-item>
    </el-descriptions>
  </Dialog>

  <!-- 审批对话框 -->
  <Dialog v-model="approveVisible" title="审批换货申请" width="560px">
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
import { onMounted, reactive, ref } from 'vue'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as MaterialExchApi from '@/api/pms/engineering/material-exch'
import * as ProjectApi from '@/api/pms/project/project'
import * as EquipmentApi from '@/api/pms/asset/equipment'
import * as UserApi from '@/api/system/user'
import type { MaterialExchangeVO } from '@/api/pms/engineering/material-exch'
import ProjectTag from '@/components/ProjectTag/index.vue'
import EquipmentTag from '@/components/EquipmentTag/index.vue'

defineOptions({ name: 'PmsEngMaterialExch' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<MaterialExchangeVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  code: '',
  name: '',
  exchangeType: '',
  crmPushStatus: '',
  status: undefined as number | undefined
})

const load = async () => {
  loading.value = true
  try {
    const data = await MaterialExchApi.getMaterialExchangePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 新建/编辑
const formVisible = ref(false)
const formRef = ref()
const form = reactive<MaterialExchangeVO>({
  projectId: undefined,
  code: '',
  name: '',
  exchangeType: 'INCOMPATIBLE',
  equipmentId: undefined,
  materialName: '',
  materialCode: '',
  specification: '',
  quantity: undefined,
  unit: '个',
  originalOrderNo: '',
  reason: '',
  reasonFiles: '',
  applicantUserId: undefined,
  applyTime: '',
  remark: ''
})
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入单号' }],
  name: [{ required: true, message: '请输入名称' }],
  exchangeType: [{ required: true, message: '请选择换货类型' }],
  materialName: [{ required: true, message: '请输入物料名称' }],
  quantity: [{ required: true, message: '请输入数量' }],
  reason: [{ required: true, message: '请输入换货原因' }],
  applicantUserId: [{ required: true, message: '请选择申请人' }],
  applyTime: [{ required: true, message: '请选择申请时间' }]
}

const openCreate = () => {
  Object.assign(form, {
    id: undefined,
    projectId: undefined,
    code: '',
    name: '',
    exchangeType: 'INCOMPATIBLE',
    equipmentId: undefined,
    materialName: '',
    materialCode: '',
    specification: '',
    quantity: undefined,
    unit: '个',
    originalOrderNo: '',
    reason: '',
    reasonFiles: '',
    applicantUserId: undefined,
    applyTime: '',
    remark: ''
  })
  formVisible.value = true
}
const openEdit = async (row: MaterialExchangeVO) => {
  const detail = await MaterialExchApi.getMaterialExchange(row.id!)
  Object.assign(form, detail)
  formVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await MaterialExchApi.updateMaterialExchange(form)
      message.success('更新成功')
    } else {
      await MaterialExchApi.createMaterialExchange(form)
      message.success('创建成功')
    }
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// 明细
const detailVisible = ref(false)
const current = ref<Partial<MaterialExchangeVO>>({})
const openDetail = async (row: MaterialExchangeVO) => {
  current.value = await MaterialExchApi.getMaterialExchange(row.id!)
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
const openApprove = (row: MaterialExchangeVO) => {
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
    await MaterialExchApi.approveMaterialExchange(approveForm as any)
    message.success('审批完成')
    approveVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// 状态操作
const handleSubmit = async (row: MaterialExchangeVO) => {
  await message.confirm('确认提交此换货申请？提交后将进入审批流程。')
  await MaterialExchApi.submitMaterialExchange(row.id!)
  message.success('提交成功')
  await load()
}
const handleWithdraw = async (row: MaterialExchangeVO) => {
  await message.confirm('确认撤回此换货申请？')
  await MaterialExchApi.withdrawMaterialExchange(row.id!)
  message.success('撤回成功')
  await load()
}
const handleTerminate = async (row: MaterialExchangeVO) => {
  await message.confirm('确认终止此换货申请？终止后不可恢复。')
  await MaterialExchApi.terminateMaterialExchange(row.id!)
  message.success('终止成功')
  await load()
}
const remove = async (row: MaterialExchangeVO) => {
  await message.delConfirm()
  await MaterialExchApi.deleteMaterialExchange(row.id!)
  message.success('删除成功')
  await load()
}

// 推送CRM
const handlePushCrm = async (row: MaterialExchangeVO) => {
  await message.confirm('确认推送此换货申请至CRM？')
  await MaterialExchApi.pushCrmMaterialExchange(row.id!)
  message.success('推送成功')
  await load()
}

onMounted(load)
</script>
