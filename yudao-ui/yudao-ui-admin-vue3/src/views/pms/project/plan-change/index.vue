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
      <el-form-item label="变更单号" prop="changeNo">
        <el-input v-model="query.changeNo" clearable class="!w-180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="变更类型" prop="changeType">
        <el-select v-model="query.changeType" clearable class="!w-160px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_PLAN_CHANGE_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_PLAN_CHANGE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openCreate()" v-hasPermi="['pms:plan-change:create']"
          ><Icon icon="ep:plus" />新建变更</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无计划变更数据">
      <el-table-column prop="changeNo" label="变更单号" width="160" />
      <el-table-column prop="projectId" label="项目" min-width="180">
        <template #default="{ row }">
          <ProjectTag :project-id="row.projectId" />
        </template>
      </el-table-column>
      <el-table-column prop="title" label="变更标题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="changeType" label="类型" width="110">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_PLAN_CHANGE_TYPE" :value="row.changeType" />
        </template>
      </el-table-column>
      <el-table-column prop="baselineVersion" label="基线版本" width="90" align="center" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_PLAN_CHANGE_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="applyTime" label="申请时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)" v-hasPermi="['pms:plan-change:query']"
            >明细</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0 || row.status === 4"
            @click="openEdit(row)"
            v-hasPermi="['pms:plan-change:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0 || row.status === 4"
            @click="handleSubmit(row)"
            v-hasPermi="['pms:plan-change:submit']"
            >提交</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 1 || row.status === 2"
            @click="openApprove(row)"
            v-hasPermi="['pms:plan-change:audit']"
            >审批</el-button
          >
          <el-button
            link
            type="info"
            v-if="row.status === 1 || row.status === 2"
            @click="handleWithdraw(row)"
            v-hasPermi="['pms:plan-change:submit']"
            >撤回</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 3"
            @click="handleApply(row)"
            v-hasPermi="['pms:plan-change:audit']"
            >应用变更</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status !== 3 && row.status !== 6"
            @click="handleTerminate(row)"
            v-hasPermi="['pms:plan-change:audit']"
            >终止</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 0 || row.status === 4"
            @click="remove(row)"
            v-hasPermi="['pms:plan-change:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 新建/编辑对话框 -->
  <Dialog v-model="formVisible" :title="form.id ? '编辑计划变更' : '新建计划变更'" width="960px">
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
              @change="handleProjectChange"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="变更单号" prop="changeNo">
            <el-input v-model="form.changeNo" :disabled="!!form.id" placeholder="如 PC-2026-001" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="变更标题" prop="title"><el-input v-model="form.title" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="变更类型" prop="changeType">
            <el-select v-model="form.changeType" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_PLAN_CHANGE_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
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
          <el-form-item label="变更原因" prop="reason">
            <Editor v-model="form.reason" height="180px" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="客户证明材料" prop="customerProofFiles">
            <UploadFile v-model="form.customerProofFiles!" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input v-model="form.remark" type="textarea" :rows="2" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-divider content-position="left">阶段计划快照（至少一条）</el-divider>
      <div class="mb-10px">
        <el-button type="primary" size="small" @click="addSnapshot" :disabled="!form.projectId">
          <Icon icon="ep:plus" />添加阶段快照
        </el-button>
        <span v-if="!form.projectId" class="ml-10px text-gray-400 text-xs">请先选择项目</span>
      </div>
      <el-table :data="form.phaseSnapshots" border max-height="320">
        <el-table-column label="阶段" width="220">
          <template #default="{ row, $index }">
            <el-select
              v-model="row.phaseId"
              placeholder="请选择阶段"
              filterable
              @change="(val) => onPhaseChange(val, $index)"
            >
              <el-option
                v-for="ph in projectPhases"
                :key="ph.id"
                :label="`${ph.code} - ${ph.name}`"
                :value="ph.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="变更前计划开始" width="200">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.beforePlanStart"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="变更前开始"
              class="!w-full"
            />
          </template>
        </el-table-column>
        <el-table-column label="变更前计划结束" width="200">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.beforePlanEnd"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="变更前结束"
              class="!w-full"
            />
          </template>
        </el-table-column>
        <el-table-column label="变更后计划开始" width="200">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.afterPlanStart"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="变更后开始"
              class="!w-full"
            />
          </template>
        </el-table-column>
        <el-table-column label="变更后计划结束" width="200">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.afterPlanEnd"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="变更后结束"
              class="!w-full"
            />
          </template>
        </el-table-column>
        <el-table-column label="变更说明" min-width="180">
          <template #default="{ row }">
            <el-input v-model="row.changeRemark" placeholder="阶段变更说明" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeSnapshot($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>

  <!-- 明细对话框 -->
  <Dialog v-model="detailVisible" title="计划变更明细" width="960px">
    <el-descriptions :column="2" border class="mb-15px">
      <el-descriptions-item label="变更单号">{{ current.changeNo }}</el-descriptions-item>
      <el-descriptions-item label="变更标题">{{ current.title }}</el-descriptions-item>
      <el-descriptions-item label="项目"><ProjectTag :project-id="current.projectId" /></el-descriptions-item>
      <el-descriptions-item label="变更类型">
        <dict-tag :type="DICT_TYPE.PMS_PLAN_CHANGE_TYPE" :value="current.changeType" />
      </el-descriptions-item>
      <el-descriptions-item label="状态">
        <dict-tag :type="DICT_TYPE.PMS_PLAN_CHANGE_STATUS" :value="current.status ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="基线版本">{{ current.baselineVersion }}</el-descriptions-item>
      <el-descriptions-item v-if="current.newBaselineVersion" label="新基线版本">
        {{ current.newBaselineVersion }}
      </el-descriptions-item>
      <el-descriptions-item label="申请时间">{{ current.applyTime }}</el-descriptions-item>
      <el-descriptions-item label="变更原因" :span="2">
        <div v-html="current.reason"></div>
      </el-descriptions-item>
      <el-descriptions-item v-if="current.approveOpinion" label="审批意见" :span="2">
        {{ current.approveOpinion }}
      </el-descriptions-item>
    </el-descriptions>
    <el-table :data="detailSnapshots" border max-height="320">
      <el-table-column prop="phaseName" label="阶段名称" min-width="140" />
      <el-table-column prop="beforePlanStart" label="变更前开始" width="160" />
      <el-table-column prop="beforePlanEnd" label="变更前结束" width="160" />
      <el-table-column prop="afterPlanStart" label="变更后开始" width="160" />
      <el-table-column prop="afterPlanEnd" label="变更后结束" width="160" />
      <el-table-column prop="changeRemark" label="变更说明" min-width="180" show-overflow-tooltip />
    </el-table>
  </Dialog>

  <!-- 审批对话框 -->
  <Dialog v-model="approveVisible" title="审批计划变更" width="560px">
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
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import * as PlanChangeApi from '@/api/pms/project/plan-change'
import * as ProjectApi from '@/api/pms/project/project'
import * as ProjectPhaseApi from '@/api/pms/project/project-phase'
import * as UserApi from '@/api/system/user'
import type { PlanChangePhaseSnapshotVO, PlanChangeVO } from '@/api/pms/project/plan-change'
import type { ProjectPhaseVO } from '@/api/pms/project/project-phase'

defineOptions({ name: 'PmsPlanChange' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<PlanChangeVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  changeNo: '',
  changeType: '',
  status: undefined as number | undefined
})

const load = async () => {
  loading.value = true
  try {
    const data = await PlanChangeApi.getPlanChangePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 新建/编辑
const formVisible = ref(false)
const formRef = ref()
const projectPhases = ref<ProjectPhaseVO[]>([])
const form = reactive<PlanChangeVO>({
  projectId: undefined!,
  changeNo: '',
  title: '',
  changeType: 'PLAN_ADJUST',
  reason: '',
  customerProofFiles: '',
  applicantUserId: undefined!,
  applyTime: '',
  baselineVersion: 0,
  remark: '',
  phaseSnapshots: []
})
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  changeNo: [{ required: true, message: '请输入变更单号' }],
  title: [{ required: true, message: '请输入变更标题' }],
  changeType: [{ required: true, message: '请选择变更类型' }],
  reason: [{ required: true, message: '请输入变更原因' }],
  applicantUserId: [{ required: true, message: '请选择申请人' }],
  applyTime: [{ required: true, message: '请选择申请时间' }]
}

const handleProjectChange = async () => {
  form.phaseSnapshots = []
  if (!form.projectId) {
    projectPhases.value = []
    return
  }
  projectPhases.value = await ProjectPhaseApi.getProjectPhaseListByProjectId(form.projectId)
}
const onPhaseChange = (val: number, index: number) => {
  const ph = projectPhases.value.find((p) => p.id === val)
  if (ph) {
    form.phaseSnapshots[index].phaseName = ph.name
    form.phaseSnapshots[index].beforePlanStart = ph.planStartTime as any
    form.phaseSnapshots[index].beforePlanEnd = ph.planEndTime as any
  }
}
const addSnapshot = () => {
  form.phaseSnapshots.push({
    phaseId: undefined as any,
    phaseName: '',
    beforePlanStart: undefined,
    beforePlanEnd: undefined,
    afterPlanStart: undefined,
    afterPlanEnd: undefined,
    changeRemark: ''
  } as PlanChangePhaseSnapshotVO)
}
const removeSnapshot = (index: number) => {
  form.phaseSnapshots.splice(index, 1)
}

const openCreate = () => {
  Object.assign(form, {
    id: undefined,
    projectId: undefined,
    changeNo: '',
    title: '',
    changeType: 'PLAN_ADJUST',
    reason: '',
    customerProofFiles: '',
    applicantUserId: undefined,
    applyTime: '',
    baselineVersion: 0,
    remark: '',
    phaseSnapshots: []
  })
  projectPhases.value = []
  formVisible.value = true
}
const openEdit = async (row: PlanChangeVO) => {
  const detail = await PlanChangeApi.getPlanChange(row.id!)
  const snapshots = await PlanChangeApi.getPlanChangeSnapshots(row.id!)
  Object.assign(form, detail, { phaseSnapshots: snapshots })
  if (form.projectId) {
    projectPhases.value = await ProjectPhaseApi.getProjectPhaseListByProjectId(form.projectId)
  }
  formVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  if (!form.phaseSnapshots || form.phaseSnapshots.length === 0) {
    message.warning('请至少添加一条阶段快照')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await PlanChangeApi.updatePlanChange(form)
      message.success('更新成功')
    } else {
      await PlanChangeApi.createPlanChange(form)
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
const current = ref<PlanChangeVO>({
  projectId: 0,
  changeNo: '',
  title: '',
  changeType: '',
  reason: '',
  applicantUserId: 0,
  applyTime: '',
  phaseSnapshots: []
})
const detailSnapshots = ref<PlanChangePhaseSnapshotVO[]>([])
const openDetail = async (row: PlanChangeVO) => {
  current.value = await PlanChangeApi.getPlanChange(row.id!)
  detailSnapshots.value = await PlanChangeApi.getPlanChangeSnapshots(row.id!)
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
const openApprove = (row: PlanChangeVO) => {
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
    await PlanChangeApi.approvePlanChange(approveForm as any)
    message.success('审批完成')
    approveVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// 状态操作
const handleSubmit = async (row: PlanChangeVO) => {
  await message.confirm('确认提交此计划变更？提交后将进入审批流程。')
  await PlanChangeApi.submitPlanChange(row.id!)
  message.success('提交成功')
  await load()
}
const handleWithdraw = async (row: PlanChangeVO) => {
  await message.confirm('确认撤回此计划变更？')
  await PlanChangeApi.withdrawPlanChange(row.id!)
  message.success('撤回成功')
  await load()
}
const handleTerminate = async (row: PlanChangeVO) => {
  await message.confirm('确认终止此计划变更？终止后不可恢复。')
  await PlanChangeApi.terminatePlanChange(row.id!)
  message.success('终止成功')
  await load()
}
const handleApply = async (row: PlanChangeVO) => {
  await message.confirm('确认将变更应用到项目阶段？将更新各阶段计划开始/结束时间，形成新基线。')
  await PlanChangeApi.applyPlanChange(row.id!)
  message.success('应用成功，已生成新基线')
  await load()
}
const remove = async (row: PlanChangeVO) => {
  await message.delConfirm()
  await PlanChangeApi.deletePlanChange(row.id!)
  message.success('删除成功')
  await load()
}

// 项目名称展示子组件（轻量内联）
const ProjectTag = {
  props: ['projectId'],
  template: '<span>{{ projectId }}</span>'
}

onMounted(load)
</script>
