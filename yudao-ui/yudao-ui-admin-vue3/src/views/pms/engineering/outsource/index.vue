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
      <el-form-item label="类型" prop="outsourceType">
        <el-select v-model="query.outsourceType" clearable class="!w-140px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_OUTSOURCE_TYPE)"
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
        <el-button type="primary" @click="openCreate()" v-hasPermi="['pms:res-outsource:create']"
          ><Icon icon="ep:plus" />新建外包申请</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无外包申请数据">
      <el-table-column prop="code" label="单号" width="160" />
      <el-table-column prop="projectId" label="项目" min-width="180">
        <template #default="{ row }">
          <ProjectTag :project-id="row.projectId" />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="outsourceType" label="类型" width="110">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_OUTSOURCE_TYPE" :value="row.outsourceType" />
        </template>
      </el-table-column>
      <el-table-column label="工作量" width="110">
        <template #default="{ row }">
          {{ row.workQuantity ?? '-' }} {{ row.workUnit || '' }}
        </template>
      </el-table-column>
      <el-table-column label="预估费用" width="120">
        <template #default="{ row }">
          {{ row.estimatedCost ?? '-' }} {{ row.currency || '' }}
        </template>
      </el-table-column>
      <el-table-column prop="plannedStartDate" label="计划开始" width="110" />
      <el-table-column prop="plannedEndDate" label="计划结束" width="110" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_APPROVAL_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="applyTime" label="申请时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)" v-hasPermi="['pms:res-outsource:query']"
            >明细</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0 || row.status === 4"
            @click="openEdit(row)"
            v-hasPermi="['pms:res-outsource:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0 || row.status === 4"
            @click="handleSubmit(row)"
            v-hasPermi="['pms:res-outsource:submit']"
            >提交</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 1 || row.status === 2"
            @click="openApprove(row)"
            v-hasPermi="['pms:res-outsource:audit']"
            >审批</el-button
          >
          <el-button
            link
            type="info"
            v-if="row.status === 1 || row.status === 2"
            @click="handleWithdraw(row)"
            v-hasPermi="['pms:res-outsource:submit']"
            >撤回</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status !== 3 && row.status !== 6"
            @click="handleTerminate(row)"
            v-hasPermi="['pms:res-outsource:audit']"
            >终止</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 0 || row.status === 4"
            @click="remove(row)"
            v-hasPermi="['pms:res-outsource:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 新建/编辑对话框 -->
  <OutsourceRequestFormDialog ref="formRef" v-model="formVisible" :form="form"
    :source-survey-id="sourceSurveyId" :saving="saving" @save="save" @attachment-changed="attachmentOwnerChanged" />

  <!-- 明细对话框 -->
  <OutsourceRequestDetailDialog v-model="detailVisible" :current="current" />

  <!-- 审批对话框 -->
  <Dialog v-model="approveVisible" title="审批外包申请" width="560px">
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
import OutsourceRequestFormDialog from './OutsourceRequestFormDialog.vue'
import OutsourceRequestDetailDialog from './OutsourceRequestDetailDialog.vue'
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { dateFormatter, formatDate } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as OutsourceApi from '@/api/pms/engineering/outsource'
import * as ProjectApi from '@/api/pms/project/projects'
import * as UserApi from '@/api/system/user'
import type { OutsourceRequestVO } from '@/api/pms/engineering/outsource'
import ProjectTag from '@/components/ProjectTag/index.vue'
import * as SiteSurveyApi from '@/api/pms/engineering/site-survey/entity'
import { positiveShortcutId, surveyPath, surveyExecutionQuery } from '@/views/pms/delivery-business/site-survey/siteSurveyOutsource'
import { resolveSurveyExecution } from '@/views/pms/delivery-business/site-survey/siteSurveyExecutionShortcut'
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'PmsEngOutsource' })
const message = useMessage()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const sourceSurveyId = ref<number>()
const loading = ref(false)
const saving = ref(false)
const rows = ref<OutsourceRequestVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  code: '',
  name: '',
  outsourceType: '',
  status: undefined as number | undefined,
  triggerSource: ''
})

const load = async () => {
  loading.value = true
  try {
    const data = await OutsourceApi.getOutsourceRequestPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 新建/编辑
const formVisible = ref(false)
const formRef = ref()
const form = reactive<OutsourceRequestVO>({
  projectId: undefined!,
  code: '',
  name: '',
  outsourceType: 'LABOR',
  workContent: '',
  workQuantity: undefined,
  workUnit: '',
  plannedStartDate: '',
  plannedEndDate: '',
  estimatedCost: undefined,
  actualCost: undefined,
  currency: 'CNY',
  vendorName: '',
  contactPhone: '',
  attachmentFiles: '',
  triggerSource: 'MANUAL',
  applicantUserId: undefined!,
  applyTime: '',
  remark: ''
})

const openCreate = () => {
  sourceSurveyId.value = undefined
  Object.assign(form, {
    id: undefined,
    projectId: undefined,
    code: '',
    name: '',
    outsourceType: 'LABOR',
    workContent: '',
    workQuantity: undefined,
    workUnit: '',
    plannedStartDate: '',
    plannedEndDate: '',
    estimatedCost: undefined,
    actualCost: undefined,
    currency: 'CNY',
    vendorName: '',
    contactPhone: '',
    attachmentFiles: '',
    triggerSource: 'MANUAL',
    triggerRefId: undefined,
    siteSurveyExecution: undefined,
    version: undefined,
    applicantUserId: undefined,
    applyTime: '',
    remark: ''
  })
  formVisible.value = true
}
const openEdit = async (row: OutsourceRequestVO) => {
  const detail = await OutsourceApi.getOutsourceRequest(row.id!)
  Object.assign(form, detail, { siteSurveyExecution: undefined })
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
      await OutsourceApi.updateOutsourceRequest(form)
      message.success('更新成功')
    } else {
      await OutsourceApi.createOutsourceRequest(form)
      message.success(sourceSurveyId.value ? '转包申请草稿已创建，ID已关联回工勘；尚未提交审批。' : '创建成功')
    }
    formVisible.value = false
    if (sourceSurveyId.value) await router.push({ path: surveyPath, query: {
      surveyId: String(sourceSurveyId.value), ...surveyExecutionQuery(form.siteSurveyExecution,
        typeof route.query.surveyStageCode === 'string' ? route.query.surveyStageCode : undefined)
    } })
    else await load()
  } finally {
    saving.value = false
  }
}

// 明细
const detailVisible = ref(false)
const current = ref<Partial<OutsourceRequestVO>>({})
const openDetail = async (row: OutsourceRequestVO) => {
  current.value = await OutsourceApi.getOutsourceRequest(row.id!)
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
const openApprove = (row: OutsourceRequestVO) => {
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
    await OutsourceApi.approveOutsourceRequest(approveForm as any)
    message.success('审批完成')
    approveVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// 状态操作
const handleSubmit = async (row: OutsourceRequestVO) => {
  await message.confirm('确认提交此外包申请？提交后将进入审批流程。')
  await OutsourceApi.submitOutsourceRequest(row.id!)
  message.success('提交成功')
  await load()
}
const handleWithdraw = async (row: OutsourceRequestVO) => {
  await message.confirm('确认撤回此外包申请？')
  await OutsourceApi.withdrawOutsourceRequest(row.id!)
  message.success('撤回成功')
  await load()
}
const handleTerminate = async (row: OutsourceRequestVO) => {
  await message.confirm('确认终止此外包申请？终止后不可恢复。')
  await OutsourceApi.terminateOutsourceRequest(row.id!)
  message.success('终止成功')
  await load()
}
const remove = async (row: OutsourceRequestVO) => {
  const execution = row.triggerSource === 'SITE_SURVEY' ? await resolveSurveyExecution(row.projectId, route.query) : undefined
  await message.delConfirm()
  await OutsourceApi.deleteOutsourceRequest(row.id!, execution)
  message.success('删除成功')
  await load()
}

onMounted(load)
watch(() => [route.query.siteSurveyId, route.query.requestId, route.query.surveyTaskId, route.query.surveyStageId,
  route.query.surveyStageCode, route.query.surveyExecutionId], async ([surveyValue, requestValue], _previous, onCleanup) => {
  let cancelled = false
  onCleanup(() => { cancelled = true })
  if (surveyValue) {
    const surveyId = positiveShortcutId(surveyValue)
    if (!surveyId) { message.warning('工勘来源编号无效'); return }
    try {
      const survey = await SiteSurveyApi.getSiteSurvey(surveyId)
      if (cancelled) return
      if (!survey || survey.status !== 0 || !survey.outsourceRequired || survey.outsourceRequestId) {
        message.warning('请从已保存且尚未关联申请的工勘草稿发起转包'); return
      }
      const execution = await resolveSurveyExecution(survey.projectId, route.query)
      if (cancelled) return
      openCreate()
      sourceSurveyId.value = surveyId
      Object.assign(form, { projectId: survey.projectId, name: `工勘转包：${survey.name}`,
        triggerSource: 'SITE_SURVEY', triggerRefId: surveyId, siteSurveyExecution: execution,
        applicantUserId: userStore.getUser.id, applyTime: formatDate(new Date()),
        workContent: survey.constructionResource || survey.conclusion || '现场工勘转包申请' })
    } catch { if (!cancelled) message.warning('工勘来源或执行轮次已不可用，请返回原任务或阶段重试') }
  } else if (requestValue) {
    const id = positiveShortcutId(requestValue)
    if (!id) { message.warning('转包申请编号无效'); return }
    const data = await OutsourceApi.getOutsourceRequest(id)
    if (cancelled) return
    if (!data) { message.warning('关联申请不存在或不可见，工勘中的来源ID保留'); return }
    current.value = data
    detailVisible.value = true
  }
}, { immediate: true })
</script>

<style scoped>
@media (max-width: 767px) {
  .outsource-input-form :deep(.el-col-12) { max-width: 100%; flex-basis: 100%; }
  .outsource-input-form :deep(.el-form-item) { display: block; }
  .outsource-input-form :deep(.el-form-item__label) { width: 100% !important; justify-content: flex-start; }
  .outsource-input-form :deep(.el-form-item__content) { margin-left: 0 !important; }
}
</style>
