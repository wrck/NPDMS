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
        <el-button type="primary" @click="openCreate()" v-hasPermi="['pms:imp-material-exch:create']"
          ><Icon icon="ep:plus" />新建换货申请</el-button
        >
      </el-form-item>
    </el-form>
    <el-alert title="CRM外部接口未接入：保留内部换货申请办理，暂不推送或更新CRM结果。" type="info" :closable="false" />
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无物料换货数据">
      <el-table-column prop="code" label="单号" width="160" />
      <el-table-column prop="projectId" label="项目" min-width="180">
        <template #default="{ row }">
          <ProjectTag :project-id="row.projectId" />
        </template>
      </el-table-column>
      <el-table-column prop="exchangeType" label="换货类型" width="110">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_MATERIAL_EXCH_TYPE" :value="row.exchangeType" />
        </template>
      </el-table-column>
      <el-table-column label="物料编码" min-width="160">
        <template #default="{ row }">
          <template v-if="row.productCode">
            <el-tag v-for="code in row.productCode.split(',').filter(Boolean)" :key="code" size="small"
              class="mr-4px">{{ code }}</el-tag>
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="数量" width="100">
        <template #default="{ row }">
          {{ row.quantity ?? '-' }} {{ row.unit || '' }}
        </template>
      </el-table-column>
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
          <el-button link type="primary" @click="openDetail(row)" v-hasPermi="['pms:imp-material-exch:query']"
            >明细</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0 || row.status === 4"
            @click="openEdit(row)"
            v-hasPermi="['pms:imp-material-exch:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0 || row.status === 4"
            @click="handleSubmit(row)"
            v-hasPermi="['pms:imp-material-exch:submit']"
            >提交</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 1 || row.status === 2"
            @click="openApprove(row)"
            v-hasPermi="['pms:imp-material-exch:audit']"
            >审批</el-button
          >
          <el-button
            link
            type="info"
            v-if="row.status === 1 || row.status === 2"
            @click="handleWithdraw(row)"
            v-hasPermi="['pms:imp-material-exch:submit']"
            >撤回</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.crmPushStatus === 'PENDING' && row.status === 3"
            disabled
            title="CRM外部接口仅预留扩展入口，当前不执行推送"
            v-hasPermi="['pms:imp-material-exch:push-crm']"
            >CRM未接入</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status !== 3 && row.status !== 6"
            @click="handleTerminate(row)"
            v-hasPermi="['pms:imp-material-exch:audit']"
            >终止</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 0 || row.status === 4"
            @click="remove(row)"
            v-hasPermi="['pms:imp-material-exch:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 新建/编辑对话框 -->
  <Dialog v-model="formVisible" :title="form.id ? '编辑换货申请' : '新建换货申请'" width="min(960px, 95vw)">
    <el-alert v-if="sourceSurveyId" title="此入口只创建内部换货申请草稿；CRM推送尚未接入，不会自动推送。" type="warning" :closable="false" />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-form-item label="设备清单">
        <MaterialDevicePicker v-if="formVisible" :key="pickerKey" :project-id="form.projectId"
          :model-value="form.serials || []" @update:model-value="updateSerials" />
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="项目" prop="projectId">
            <el-input v-if="sourceSurveyId || props.projectId" :model-value="sourceSurveyId ? `工勘所属项目 #${form.projectId}` : projectLabel" disabled />
            <PmsEntitySelect
              v-else
              v-model="form.projectId"
              :api="ProjectApi.getProjectPage"
              label-field="projectName"
              value-field="id"
              query-field="projectName"
              @change="changeProject"
              placeholder="请选择项目"
              :disabled="!!form.id"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="单号" prop="code">
            <el-input v-model="form.code" disabled placeholder="保存时自动生成" />
          </el-form-item>
        </el-col>
        <el-col v-if="!props.projectId" :span="12">
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
        <el-col :span="24">
          <el-form-item label="物料编码">
            <template v-if="derivedItemCodes.length">
              <el-tag v-for="code in derivedItemCodes" :key="code" size="small" class="mr-4px">{{ code }}</el-tag>
            </template>
            <span v-else class="el-form-item__info">勾选清单行后按物料编码去重拼接</span>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="数量" prop="quantity">
            <el-input-number v-model="form.quantity" :min="0" :precision="2" :disabled="!!form.serials?.length" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="单位" prop="unit">
            <el-input v-model="form.unit" placeholder="如 个/台/套" />
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
              placeholder="申请人默认当前用户"
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
            <NativeAttachments :key="String(form.id || 'new')" kind="materialExchange" :entity-id="form.id"
              :readonly="![0, 4].includes(form.status ?? -1) || !checkPermi(['pms:imp-material-exch:update'])" :legacy="form.reasonFiles" @changed="attachmentOwnerChanged" />
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
  <Dialog v-model="detailVisible" title="换货申请明细" width="min(960px, 95vw)">
    <el-descriptions :column="2" border class="mb-15px">
      <el-descriptions-item label="单号">{{ current.code }}</el-descriptions-item>
      <el-descriptions-item label="名称">{{ current.name }}</el-descriptions-item>
      <el-descriptions-item label="项目"><ProjectTag :project-id="current.projectId" /></el-descriptions-item>
      <el-descriptions-item label="换货类型">
        <dict-tag :type="DICT_TYPE.PMS_MATERIAL_EXCH_TYPE" :value="current.exchangeType ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="物料编码">
        <template v-if="current.productCode">
          <el-tag v-for="code in current.productCode.split(',').filter(Boolean)" :key="code" size="small"
            class="mr-4px">{{ code }}</el-tag>
        </template>
        <span v-else>-</span>
      </el-descriptions-item>
      <el-descriptions-item label="产品型号">{{ current.productModel }}</el-descriptions-item>
      <el-descriptions-item label="数量">{{ current.quantity }} {{ current.unit }}</el-descriptions-item>
      <el-descriptions-item label="申请人">{{ current.applicantUserId }}</el-descriptions-item>
      <el-descriptions-item label="申请时间">{{ current.applyTime }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <dict-tag :type="DICT_TYPE.PMS_APPROVAL_STATUS" :value="current.status ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="CRM推送状态">
        <dict-tag :type="DICT_TYPE.PMS_CRM_SYNC_STATUS" :value="current.crmPushStatus ?? ''" />
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
    <NativeAttachments v-if="current.id" kind="materialExchange" :entity-id="current.id" :readonly="true" :legacy="current.reasonFiles" />
    <el-table v-if="current.serials?.length" :data="current.serials" border max-height="360" class="mt-4">
      <el-table-column prop="orderNo" label="订单号" min-width="140" />
      <el-table-column prop="lineNo" label="行号" width="80" />
      <el-table-column prop="itemCode" label="物料编码" min-width="130" show-overflow-tooltip />
      <el-table-column label="换货产品" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.productName || '-' }}</template>
      </el-table-column>
      <el-table-column label="设备类型" width="110">
        <template #default="{ row }">{{ row.deviceTypeName || row.deviceTypeCode }}</template>
      </el-table-column>
      <el-table-column label="换货数量" width="100">
        <template #default="{ row }">{{ row.quantity ?? 1 }}</template>
      </el-table-column>
    </el-table>
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
import { checkPermi } from '@/utils/permission'
import NativeAttachments from '../attachment/NativeAttachments.vue'
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { positiveShortcutId, surveyPath } from '@/views/pms/delivery-business/site-survey/siteSurveyOutsource'
import { loadSurveyActionContext } from '@/views/pms/delivery-business/site-survey/surveyActionContext'
import { dateFormatter, formatDate } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as MaterialExchApi from '@/api/pms/engineering/material-exch'
import * as ProjectApi from '@/api/pms/project/projects'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'
import * as UserApi from '@/api/system/user'
import MaterialDevicePicker from './MaterialDevicePicker.vue'
import type { MaterialExchangeVO, MaterialExchangeSerialVO } from '@/api/pms/engineering/material-exch'
import ProjectTag from '@/components/ProjectTag/index.vue'

defineOptions({ name: 'PmsEngMaterialExch' })
const props = defineProps<{ projectId?: number }>()
const message = useMessage()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const sourceSurveyId = ref<number>()
const loading = ref(false)
const saving = ref(false)
const rows = ref<MaterialExchangeVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: props.projectId as number | undefined,
  code: '',
  name: '',
  exchangeType: '',
  crmPushStatus: '',
  status: undefined as number | undefined
})

const projectLabel = ref('')
const pickerKey = ref(0)
/** 换货数量 = 各勾选设备行换货数量之和 */
const serialQuantityTotal = (serials: MaterialExchangeSerialVO[]) =>
  serials.reduce((sum, row) => sum + (row.quantity ?? 1), 0)
/** 主表物料编码 = 勾选清单行物料编码去重（服务端拼接持久化，此处只读派生展示） */
const derivedItemCodes = computed(() =>
  [...new Set((form.serials || []).map(row => row.itemCode).filter(Boolean))] as string[])
/** 项目编码、名称按当前项目自动填充；用户已录入时不覆盖 */
const prefillProjectFields = (project?: { projectCode?: string; projectName?: string }) => {
  if (project?.projectName) form.name ||= project.projectName
  if (project?.projectCode) form.code ||= `ME-${project.projectCode}-${Date.now()}`
}
const changeProject = (_val: number, selected?: { projectCode?: string; projectName?: string }) => {
  form.serials = []
  form.deviceId = undefined
  form.quantity = undefined!
  prefillProjectFields(selected)
}
const updateSerials = (serials: MaterialExchangeSerialVO[]) => {
  form.serials = serials
  form.deviceId = serials.find(row => row.deviceId)?.deviceId
  if (serials.length) {
    form.quantity = serialQuantityTotal(serials)
    form.unit = '台'
  }
}
const loadProjectLabel = async () => {
  if (!props.projectId || projectLabel.value) return
  try {
    const detail = await ProjectApi.getProject(props.projectId)
    projectLabel.value = detail?.projectName
      ? `${detail.projectName}（${detail.projectCode || `#${props.projectId}`}）`
      : `#${props.projectId}`
    prefillProjectFields(detail)
  } catch {
    projectLabel.value = `#${props.projectId}`
  }
}

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
  projectId: undefined!,
  code: '',
  name: '',
  exchangeType: 'INCOMPATIBLE',
  deviceId: undefined,
  serials: [],
  quantity: undefined!,
  unit: '个',
  reason: '',
  reasonFiles: '',
  applicantUserId: undefined!,
  applyTime: '',
  remark: ''
})
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  name: [{ required: true, message: '请输入名称' }],
  exchangeType: [{ required: true, message: '请选择换货类型' }],
  quantity: [{ required: true, message: '请输入数量' }],
  reason: [{ required: true, message: '请输入换货原因' }],
  applicantUserId: [{ required: true, message: '请选择申请人' }]
}

const openCreate = () => {
  sourceSurveyId.value = undefined
  form.version = undefined
  Object.assign(form, {
    id: undefined,
    projectId: props.projectId,
    code: '',
    name: '',
    exchangeType: 'INCOMPATIBLE',
    deviceId: undefined,
  serials: [],
    quantity: undefined,
    unit: '个',
    reason: '',
    reasonFiles: '',
    applicantUserId: userStore.getUser.id,
    applyTime: '',
    remark: ''
  })
  if (props.projectId) {
    loadProjectLabel()

  }
  pickerKey.value++
  formVisible.value = true
}
const openEdit = async (row: MaterialExchangeVO) => {
  const detail = await MaterialExchApi.getMaterialExchange(row.id!)
  Object.assign(form, detail, { serials: detail.serials || [] })
  // 旧申请没有子表时保留原设备；下一次显式保存才生成快照。
  if (!form.serials?.length && detail.deviceId) {
    const device = await DeviceArchiveApi.getDeviceArchiveRecord(detail.deviceId)
    form.serials = [{ deviceId: detail.deviceId, quantity: 1, sn: device.sn, productName: device.name,
      productCode: device.productCode, productModel: device.productModel, contractNo: device.contractNo }]
  }
  pickerKey.value++
  formVisible.value = true
}
const attachmentOwnerChanged = (saved: { version?: number; status?: number; configLogUrl?: string }) => {
  form.version = saved.version
  form.status = saved.status
}
const save = async () => {
  if (saving.value) return
  // 单号自动生成：编号只读展示，创建时无项目前缀也按规则生成
  if (!form.id && !form.code) form.code = `ME-${Date.now()}`
  await formRef.value.validate()
  // 数量自动取各设备行换货数量之和；申请时间创建时自动取当前时间，不再手工录入。
  if (form.serials?.length) form.quantity = serialQuantityTotal(form.serials)
  if (!form.id) form.applyTime = formatDate(new Date())
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
    if (sourceSurveyId.value) await router.push({ path: surveyPath, query: { surveyId: String(sourceSurveyId.value) } })
    else await load()
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

onMounted(load)
watch(() => [route.query.surveyId, route.query.deviceSn], async ([value, sn]) => {
  if (!value) return
  const surveyId = positiveShortcutId(value)
  if (!surveyId) { message.warning('工勘来源编号无效'); return }
  try {
    const context = await loadSurveyActionContext(surveyId, 'exchange', typeof sn === 'string' ? sn : undefined)
    openCreate()
    sourceSurveyId.value = surveyId
    Object.assign(form, context, { applicantUserId: userStore.getUser.id })
  } catch(error) { message.warning(error instanceof Error ? error.message : '工勘来源读取失败') }
}, { immediate: true })
</script>

<style lang="scss" scoped>
.material-pick {
  width: 100%;
}

.material-pick-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
</style>
