<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="任务编号" prop="taskId">
        <PmsEntitySelect
          v-model="query.taskId"
          :api="CutTaskApi.getCutTaskPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择割接任务"
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item label="方案编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="方案名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_CUTOVER_PLAN_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:cut-plan:create']"
          ><Icon icon="ep:plus" />新增方案</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="方案编码" min-width="140" />
      <el-table-column prop="name" label="方案名称" min-width="180" />
      <el-table-column prop="level" label="等级" width="80" />
      <el-table-column prop="baselineVersion" label="基线版本" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_CUTOVER_PLAN_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="420" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:cut-plan:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'submitPlanForReview', '提交评审')"
            v-hasPermi="['pms:cut-plan:update']"
            >提交评审</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 1"
            @click="openApprove(row, 'approve')"
            v-hasPermi="['pms:cut-plan:audit']"
            >评审通过</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 1"
            @click="openApprove(row, 'reject')"
            v-hasPermi="['pms:cut-plan:audit']"
            >评审驳回</el-button
          >
          <el-button
            link
            type="danger"
            v-if="![2, 4].includes(row.status)"
            @click="handleAction(row, 'terminateCutPlan', '终止')"
            v-hasPermi="['pms:cut-plan:update']"
            >终止</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:cut-plan:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑方案' : '新增方案'" width="820px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="任务编号" prop="taskId">
            <PmsEntitySelect
              v-model="form.taskId"
              :api="CutTaskApi.getCutTaskPage"
              label-field="name"
              value-field="id"
              query-field="name"
              placeholder="请选择割接任务"
              :disabled="!!form.id"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="方案编码" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="方案名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="方案等级" prop="level">
            <el-select v-model="form.level" clearable class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_RISK_LEVEL)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="割接前检查" prop="preCheck">
            <el-input v-model="form.preCheck" type="textarea" :rows="3" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="割接步骤" prop="procedure">
            <el-input v-model="form.procedure" type="textarea" :rows="4" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="业务验证" prop="verification">
            <el-input v-model="form.verification" type="textarea" :rows="3" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="回退方案" prop="rollback">
            <Editor v-model="form.rollback" :height="300" />
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

  <Dialog v-model="approveVisible" :title="approveTitle" width="500px">
    <el-form :model="approveForm" label-width="100px">
      <el-form-item label="审核意见">
        <el-input v-model="approveForm.approvalOpinion" type="textarea" :rows="4" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="approveVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitApprove">确认</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as CutPlanApi from '@/api/pms/cutover/cut-plan'
import * as CutTaskApi from '@/api/pms/cutover/cut-task'
import type { CutPlanVO } from '@/api/pms/cutover/cut-plan'

defineOptions({ name: 'PmsCutPlan' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<CutPlanVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, taskId: '', code: '', name: '', status: undefined })
const formVisible = ref(false)
const formRef = ref()
const form = reactive<CutPlanVO>({ taskId: 0, code: '', name: '' })
const rules = {
  taskId: [{ required: true, message: '请输入任务编号' }],
  code: [{ required: true, message: '请输入方案编码' }],
  name: [{ required: true, message: '请输入方案名称' }]
}

const approveVisible = ref(false)
const approveTitle = ref('')
const approveForm = reactive({ id: 0, approvalOpinion: '', version: undefined as number | undefined, action: '' })

const load = async () => {
  loading.value = true
  try {
    const data = await CutPlanApi.getCutPlanPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: CutPlanVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      taskId: 0,
      code: '',
      name: '',
      preCheck: '',
      procedure: '',
      verification: '',
      rollback: '',
      level: '',
      remark: '',
      version: undefined
    },
    row || {}
  )
  formVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    form.id ? await CutPlanApi.updateCutPlan(form) : await CutPlanApi.createCutPlan(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: CutPlanVO) => {
  await message.delConfirm()
  await CutPlanApi.deleteCutPlan(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (row: CutPlanVO, action: string, actionText: string) => {
  await message.confirm(`确认${actionText}方案【${row.code}】？`)
  await (CutPlanApi as any)[action](row.id!)
  message.success(`${actionText}成功`)
  await load()
}
const openApprove = (row: CutPlanVO, action: string) => {
  approveForm.id = row.id!
  approveForm.approvalOpinion = ''
  approveForm.version = row.version
  approveForm.action = action
  approveTitle.value = action === 'approve' ? '评审通过' : '评审驳回'
  approveVisible.value = true
}
const submitApprove = async () => {
  saving.value = true
  try {
    if (approveForm.action === 'approve') {
      await CutPlanApi.approveCutPlan({ id: approveForm.id, approvalOpinion: approveForm.approvalOpinion, version: approveForm.version })
    } else {
      await CutPlanApi.rejectCutPlan({ id: approveForm.id, approvalOpinion: approveForm.approvalOpinion, version: approveForm.version })
    }
    message.success('操作成功')
    approveVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
