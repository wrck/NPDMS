<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="项目编号" prop="projectId">
        <PmsEntitySelect
          v-model="query.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择项目"
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item label="任务编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="任务名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-160px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_CUTOVER_TASK_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="等级" prop="riskLevel">
        <el-select v-model="query.riskLevel" clearable class="!w-100px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_RISK_LEVEL)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:cut-task:create']"
          ><Icon icon="ep:plus" />新增割接任务</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="任务编码" min-width="140" />
      <el-table-column prop="name" label="任务名称" min-width="180" />
      <el-table-column prop="cutoverType" label="割接类型" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_CUTOVER_TYPE" :value="row.cutoverType" />
        </template>
      </el-table-column>
      <el-table-column prop="riskLevel" label="等级" width="80">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_RISK_LEVEL" :value="row.riskLevel" />
        </template>
      </el-table-column>
      <el-table-column prop="scheduledTime" label="计划时间" width="160" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_CUTOVER_TASK_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="520" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:cut-task:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleSimpleAction(row, 'submitForReview', '提交评审')"
            v-hasPermi="['pms:cut-task:update']"
            >提交评审</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 2"
            @click="openApprove(row, 'approve')"
            v-hasPermi="['pms:cut-task:audit']"
            >评审通过</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 2"
            @click="openApprove(row, 'reject')"
            v-hasPermi="['pms:cut-task:audit']"
            >评审驳回</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:cut-task:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑割接任务' : '新增割接任务'" width="780px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="项目编号" prop="projectId">
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
          <el-form-item label="任务编码" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="任务名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="割接类型" prop="cutoverType">
            <el-select v-model="form.cutoverType" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_CUTOVER_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="组网模式" prop="networkMode">
            <el-select v-model="form.networkMode" clearable class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_NETWORK_MODE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="来源类型" prop="sourceType">
            <el-select v-model="form.sourceType" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_SOURCE_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="来源编号" prop="sourceId"><el-input v-model="form.sourceId" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="割接等级" prop="riskLevel">
            <el-select v-model="form.riskLevel" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_RISK_LEVEL)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="计划时间" prop="scheduledTime">
            <el-date-picker v-model="form.scheduledTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="!w-full" />
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
      <el-form-item label="评审意见">
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
import * as CutTaskApi from '@/api/pms/cutover/cut-task'
import * as ProjectApi from '@/api/pms/project/project'
import type { CutTaskVO } from '@/api/pms/cutover/cut-task'

defineOptions({ name: 'PmsCutTask' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<CutTaskVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, projectId: '', code: '', name: '', status: undefined, riskLevel: undefined })
const formVisible = ref(false)
const formRef = ref()
const form = reactive<CutTaskVO>({ projectId: 0, code: '', name: '' })
const rules = {
  projectId: [{ required: true, message: '请输入项目编号' }],
  code: [{ required: true, message: '请输入任务编码' }],
  name: [{ required: true, message: '请输入任务名称' }]
}

const approveVisible = ref(false)
const approveTitle = ref('')
const approveForm = reactive({ id: 0, approvalOpinion: '', version: undefined as number | undefined, action: '' })

const load = async () => {
  loading.value = true
  try {
    const data = await CutTaskApi.getCutTaskPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: CutTaskVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: 0,
      code: '',
      name: '',
      cutoverType: 'REPLACE',
      networkMode: '',
      sourceType: 'MANUAL',
      sourceId: undefined,
      riskLevel: 'C',
      scheduledTime: '',
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
    form.id ? await CutTaskApi.updateCutTask(form) : await CutTaskApi.createCutTask(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: CutTaskVO) => {
  await message.delConfirm()
  await CutTaskApi.deleteCutTask(row.id!)
  message.success('删除成功')
  await load()
}
const handleSimpleAction = async (row: CutTaskVO, action: string, actionText: string) => {
  await message.confirm(`确认${actionText}割接任务【${row.code}】？`)
  await (CutTaskApi as any)[action](row.id!)
  message.success(`${actionText}成功`)
  await load()
}
const openApprove = (row: CutTaskVO, action: string) => {
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
      await CutTaskApi.approveCutTask({ id: approveForm.id, approvalOpinion: approveForm.approvalOpinion, version: approveForm.version })
    } else {
      await CutTaskApi.rejectCutTask({ id: approveForm.id, approvalOpinion: approveForm.approvalOpinion, version: approveForm.version })
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
