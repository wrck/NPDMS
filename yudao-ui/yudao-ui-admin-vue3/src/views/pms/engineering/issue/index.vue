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
      <el-form-item label="问题编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-160px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_ISSUE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="严重等级" prop="severity">
        <el-select v-model="query.severity" clearable class="!w-120px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_ISSUE_SEVERITY)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:eng-issue:create']"
          ><Icon icon="ep:plus" />新增问题</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="问题编码" min-width="140" />
      <el-table-column prop="name" label="问题名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="source" label="来源" width="110">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_ISSUE_SOURCE" :value="row.source" />
        </template>
      </el-table-column>
      <el-table-column prop="severity" label="等级" width="80">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_ISSUE_SEVERITY" :value="row.severity" />
        </template>
      </el-table-column>
      <el-table-column prop="deadline" label="整改时限" width="160" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_ISSUE_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="460" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:eng-issue:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'startRectify')"
            v-hasPermi="['pms:eng-issue:update']"
            >开始整改</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 1"
            @click="handleAction(row, 'submitForVerify')"
            v-hasPermi="['pms:eng-issue:update']"
            >提交验证</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 2"
            @click="openVerify(row, 'close')"
            v-hasPermi="['pms:eng-issue:verify']"
            >关闭</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 2"
            @click="openVerify(row, 'reject')"
            v-hasPermi="['pms:eng-issue:verify']"
            >驳回</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status !== 3 && row.status !== 4"
            @click="handleAction(row, 'suspend')"
            v-hasPermi="['pms:eng-issue:update']"
            >挂起</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 4"
            @click="handleAction(row, 'resume')"
            v-hasPermi="['pms:eng-issue:update']"
            >恢复</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:eng-issue:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑问题' : '新增问题'" width="780px">
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
          <el-form-item label="问题编码" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="来源" prop="source">
            <el-select v-model="form.source" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_ISSUE_SOURCE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="严重等级" prop="severity">
            <el-select v-model="form.severity" class="!w-full">
              <el-option
                v-for="dict in getIntDictOptions(DICT_TYPE.PMS_ISSUE_SEVERITY)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="问题名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="问题描述" prop="description">
            <Editor v-model="form.description" height="200px" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="责任人" prop="ownerUserId">
            <PmsEntitySelect
              v-model="form.ownerUserId"
              :api="UserApi.getUserPage"
              label-field="nickname"
              value-field="id"
              query-field="nickname"
              placeholder="请选择责任人"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="整改时限" prop="deadline">
            <el-date-picker v-model="form.deadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="整改方案" prop="solution">
            <Editor v-model="form.solution" height="200px" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="验证标准" prop="verificationStandard">
            <Editor v-model="form.verificationStandard" height="200px" />
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

  <Dialog v-model="verifyVisible" :title="verifyForm.action === 'close' ? '关闭问题-复测' : '验证驳回'" width="540px">
    <el-form :model="verifyForm" label-width="100px">
      <el-form-item v-if="verifyForm.action === 'close'" label="复测结果" required>
        <el-input v-model="verifyForm.verifyResult" type="textarea" :rows="4" placeholder="请输入复测结果" />
      </el-form-item>
      <el-form-item v-else label="驳回原因" required>
        <el-input v-model="verifyForm.rejectReason" type="textarea" :rows="4" placeholder="请输入驳回原因" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="verifyVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="confirmVerify">确认</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as IssueApi from '@/api/pms/engineering/issue'
import type { IssueVO } from '@/api/pms/engineering/issue'
import * as ProjectApi from '@/api/pms/project/project'
import * as UserApi from '@/api/system/user'

defineOptions({ name: 'PmsEngIssue' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<IssueVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: '',
  code: '',
  status: undefined,
  severity: undefined
})
const formVisible = ref(false)
const formRef = ref()
const form = reactive<IssueVO>({ projectId: 0, code: '', name: '', severity: 1 })
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入问题编码' }],
  name: [{ required: true, message: '请输入问题名称' }],
  severity: [{ required: true, message: '请选择严重等级' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await IssueApi.getIssuePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: IssueVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: 0,
      code: '',
      name: '',
      description: '',
      source: 'OTHER',
      severity: 1,
      ownerUserId: undefined,
      deadline: '',
      solution: '',
      verificationStandard: '',
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
    form.id ? await IssueApi.updateIssue(form) : await IssueApi.createIssue(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: IssueVO) => {
  await message.delConfirm()
  await IssueApi.deleteIssue(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (row: IssueVO, action: 'startRectify' | 'submitForVerify' | 'suspend' | 'resume') => {
  const actionText = {
    startRectify: '开始整改',
    submitForVerify: '提交验证',
    suspend: '挂起',
    resume: '恢复'
  }[action]
  await message.confirm(`确认${actionText}问题【${row.code}】？`)
  if (action === 'startRectify') await IssueApi.startRectifyIssue(row.id!)
  if (action === 'submitForVerify') await IssueApi.submitForVerifyIssue(row.id!)
  if (action === 'suspend') await IssueApi.suspendIssue(row.id!)
  if (action === 'resume') await IssueApi.resumeIssue(row.id!)
  message.success(`${actionText}成功`)
  await load()
}
const verifyVisible = ref(false)
const verifyForm = reactive({
  id: 0,
  verifyResult: '',
  rejectReason: '',
  action: 'close' as 'close' | 'reject',
  version: undefined as number | undefined
})
const openVerify = (row: IssueVO, action: 'close' | 'reject') => {
  verifyForm.id = row.id!
  verifyForm.verifyResult = ''
  verifyForm.rejectReason = ''
  verifyForm.action = action
  verifyForm.version = row.version
  verifyVisible.value = true
}
const confirmVerify = async () => {
  if (verifyForm.action === 'close' && !verifyForm.verifyResult) {
    message.warning('请输入复测结果')
    return
  }
  if (verifyForm.action === 'reject' && !verifyForm.rejectReason) {
    message.warning('请输入驳回原因')
    return
  }
  saving.value = true
  try {
    if (verifyForm.action === 'close') await IssueApi.closeIssue(verifyForm)
    else await IssueApi.rejectIssue(verifyForm)
    message.success('操作成功')
    verifyVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
