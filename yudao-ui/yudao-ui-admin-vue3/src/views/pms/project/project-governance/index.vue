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
      <el-form-item label="动作单号" prop="actionNo">
        <el-input v-model="query.actionNo" clearable class="!w-180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="动作类型" prop="actionType">
        <el-select v-model="query.actionType" clearable class="!w-160px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_GOVERNANCE_ACTION_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_GOVERNANCE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openCreate()" v-hasPermi="['pms:project-governance:create']"
          ><Icon icon="ep:plus" />新建治理动作</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无项目治理动作">
      <el-table-column prop="actionNo" label="动作单号" width="170" />
      <el-table-column prop="projectId" label="项目编号" width="100">
        <template #default="{ row }">
          <ProjectTag :project-id="row.projectId" />
        </template>
      </el-table-column>
      <el-table-column prop="actionType" label="动作类型" width="110">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_GOVERNANCE_ACTION_TYPE" :value="row.actionType" />
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="原因" min-width="220" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_GOVERNANCE_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="applyTime" label="申请时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column prop="executeTime" label="执行时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="340" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)" v-hasPermi="['pms:project-governance:query']"
            >明细</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0 || row.status === 4"
            @click="openEdit(row)"
            v-hasPermi="['pms:project-governance:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0 || row.status === 4"
            @click="handleSubmit(row)"
            v-hasPermi="['pms:project-governance:submit']"
            >提交</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 1 || row.status === 2"
            @click="openApprove(row)"
            v-hasPermi="['pms:project-governance:audit']"
            >审批执行</el-button
          >
          <el-button
            link
            type="info"
            v-if="row.status === 1 || row.status === 2"
            @click="handleWithdraw(row)"
            v-hasPermi="['pms:project-governance:submit']"
            >撤回</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 0 || row.status === 4"
            @click="remove(row)"
            v-hasPermi="['pms:project-governance:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 新建/编辑对话框 -->
  <Dialog v-model="formVisible" :title="form.id ? '编辑治理动作' : '新建治理动作'" width="720px">
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
          <el-form-item label="动作单号" prop="actionNo">
            <el-input v-model="form.actionNo" :disabled="!!form.id" placeholder="如 GOV-2026-001" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="动作类型" prop="actionType">
            <el-radio-group v-model="form.actionType" :disabled="!!form.id">
              <el-radio value="ROLLBACK">回退总部</el-radio>
              <el-radio value="DIRECT_CLOSE">直接关闭</el-radio>
            </el-radio-group>
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
          <el-form-item label="回退/关闭原因" prop="reason">
            <Editor v-model="form.reason" height="180px" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="证明材料" prop="proofFiles">
            <UploadFile v-model="form.proofFiles" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input v-model="form.remark" type="textarea" :rows="2" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-alert
        v-if="form.actionType === 'ROLLBACK'"
        type="warning"
        title="回退总部：审批通过执行后，项目状态将置为待指派，项目经理将被清空，便于总部重新指派。"
        :closable="false"
        class="mt-10px"
      />
      <el-alert
        v-if="form.actionType === 'DIRECT_CLOSE'"
        type="warning"
        title="直接关闭：审批通过执行后，项目状态将置为已关闭。"
        :closable="false"
        class="mt-10px"
      />
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>

  <!-- 明细对话框 -->
  <Dialog v-model="detailVisible" title="治理动作明细" width="780px">
    <el-descriptions :column="2" border>
      <el-descriptions-item label="动作单号">{{ current.actionNo }}</el-descriptions-item>
      <el-descriptions-item label="动作类型">
        {{ current.actionType === 'ROLLBACK' ? '回退总部' : '直接关闭' }}
      </el-descriptions-item>
      <el-descriptions-item label="项目编号"><ProjectTag :project-id="current.projectId" /></el-descriptions-item>
      <el-descriptions-item label="状态">
        <dict-tag :type="DICT_TYPE.PMS_GOVERNANCE_STATUS" :value="current.status ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="申请人"><UserTag :user-id="current.applicantUserId" /></el-descriptions-item>
      <el-descriptions-item label="申请时间">{{ current.applyTime }}</el-descriptions-item>
      <el-descriptions-item label="审批人"><UserTag :user-id="current.approverUserId" /></el-descriptions-item>
      <el-descriptions-item label="审批时间">{{ current.approveTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="执行前项目状态">
        <dict-tag :type="DICT_TYPE.PMS_PROJECT_STATUS" :value="current.beforeProjectStatus ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="执行后项目状态">
        <dict-tag :type="DICT_TYPE.PMS_PROJECT_STATUS" :value="current.afterProjectStatus ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="原因" :span="2">
        <div v-html="current.reason"></div>
      </el-descriptions-item>
      <el-descriptions-item v-if="current.approveOpinion" label="审批意见" :span="2">
        {{ current.approveOpinion }}
      </el-descriptions-item>
      <el-descriptions-item label="执行时间" :span="2">{{ current.executeTime || '-' }}</el-descriptions-item>
    </el-descriptions>
  </Dialog>

  <!-- 审批对话框 -->
  <Dialog v-model="approveVisible" title="审批执行治理动作" width="560px">
    <el-alert
      type="warning"
      :title="`审批通过将立即执行${
        currentActionType === 'ROLLBACK' ? '回退（项目置为待指派、清空项目经理）' : '关闭（项目置为已关闭）'
      }`"
      :closable="false"
      class="mb-15px"
    />
    <el-form ref="approveFormRef" :model="approveForm" :rules="approveRules" label-width="100px">
      <el-form-item label="审批动作" prop="approveAction">
        <el-radio-group v-model="approveForm.approveAction">
          <el-radio value="PASS">通过并执行</el-radio>
          <el-radio value="REJECT">驳回</el-radio>
          <el-radio value="RETURN">退回修改</el-radio>
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
import * as GovernanceApi from '@/api/pms/project/project-governance'
import * as ProjectApi from '@/api/pms/project/project'
import * as UserApi from '@/api/system/user'
import ProjectTag from '@/components/ProjectTag/index.vue'
import UserTag from '@/components/UserTag/index.vue'
import type { ProjectGovernanceVO } from '@/api/pms/project/project-governance'

defineOptions({ name: 'PmsProjectGovernance' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<ProjectGovernanceVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  actionNo: '',
  actionType: '',
  status: undefined as number | undefined
})

const load = async () => {
  loading.value = true
  try {
    const data = await GovernanceApi.getGovernanceActionPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 新建/编辑
const formVisible = ref(false)
const formRef = ref()
const form = reactive<ProjectGovernanceVO>({
  projectId: undefined!,
  actionNo: '',
  actionType: 'ROLLBACK',
  reason: '',
  proofFiles: '',
  applicantUserId: undefined!,
  applyTime: '',
  remark: ''
})
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  actionNo: [{ required: true, message: '请输入动作单号' }],
  actionType: [{ required: true, message: '请选择动作类型' }],
  reason: [{ required: true, message: '请输入原因' }],
  applicantUserId: [{ required: true, message: '请选择申请人' }],
  applyTime: [{ required: true, message: '请选择申请时间' }]
}
const openCreate = () => {
  Object.assign(form, {
    id: undefined,
    projectId: undefined,
    actionNo: '',
    actionType: 'ROLLBACK',
    reason: '',
    proofFiles: '',
    applicantUserId: undefined,
    applyTime: '',
    remark: ''
  })
  formVisible.value = true
}
const openEdit = async (row: ProjectGovernanceVO) => {
  const detail = await GovernanceApi.getGovernanceAction(row.id!)
  Object.assign(form, detail)
  formVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await GovernanceApi.updateGovernanceAction(form)
      message.success('更新成功')
    } else {
      await GovernanceApi.createGovernanceAction(form)
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
const current = ref<Partial<ProjectGovernanceVO>>({})
const openDetail = async (row: ProjectGovernanceVO) => {
  current.value = await GovernanceApi.getGovernanceAction(row.id!)
  detailVisible.value = true
}

// 审批
const approveVisible = ref(false)
const approveFormRef = ref()
const currentActionType = ref('')
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
const openApprove = (row: ProjectGovernanceVO) => {
  currentActionType.value = row.actionType
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
  await message.confirm('确认执行此审批操作？')
  saving.value = true
  try {
    await GovernanceApi.approveGovernanceAction(approveForm as any)
    message.success('审批执行完成')
    approveVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// 状态操作
const handleSubmit = async (row: ProjectGovernanceVO) => {
  await message.confirm('确认提交此治理动作？提交后将进入审批流程。')
  await GovernanceApi.submitGovernanceAction(row.id!)
  message.success('提交成功')
  await load()
}
const handleWithdraw = async (row: ProjectGovernanceVO) => {
  await message.confirm('确认撤回此治理动作？')
  await GovernanceApi.withdrawGovernanceAction(row.id!)
  message.success('撤回成功')
  await load()
}
const remove = async (row: ProjectGovernanceVO) => {
  await message.delConfirm()
  await GovernanceApi.deleteGovernanceAction(row.id!)
  message.success('删除成功')
  await load()
}

onMounted(load)
</script>
