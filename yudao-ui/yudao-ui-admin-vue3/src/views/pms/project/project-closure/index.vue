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
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item label="闭环编号" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="闭环名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_CLOSURE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:acc-project-closure:create']"
          ><Icon icon="ep:plus" />新增项目闭环</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="闭环编号" min-width="140" />
      <el-table-column prop="name" label="闭环名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="applicationDate" label="申请日期" width="120" />
      <el-table-column prop="approverUserId" label="审批人" width="100">
        <template #default="{ row }">
          <UserTag :user-id="row.approverUserId" />
        </template>
      </el-table-column>
      <el-table-column prop="approvalDate" label="审批日期" width="120" />
      <el-table-column prop="approvalOpinion" label="审批意见" min-width="160" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_CLOSURE_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="560" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:acc-project-closure:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'submitProjectClosure', '提交')"
            v-hasPermi="['pms:acc-project-closure:update']"
            >提交</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 1"
            @click="handleAction(row, 'startApproveProjectClosure', '开始审批')"
            v-hasPermi="['pms:acc-project-closure:update']"
            >开始审批</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 2"
            @click="handleAction(row, 'passProjectClosure', '通过')"
            v-hasPermi="['pms:acc-project-closure:update']"
            >通过</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 2"
            @click="handleAction(row, 'rejectProjectClosure', '驳回')"
            v-hasPermi="['pms:acc-project-closure:update']"
            >驳回</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 3"
            @click="handleAction(row, 'archiveProjectClosure', '归档')"
            v-hasPermi="['pms:acc-project-closure:update']"
            >归档</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:acc-project-closure:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑项目闭环' : '新增项目闭环'" width="780px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
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
          <el-form-item label="闭环编号" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="闭环名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="申请日期" prop="applicationDate">
            <el-date-picker v-model="form.applicationDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="审批人" prop="approverUserId"><el-input v-model="form.approverUserId" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="审批日期" prop="approvalDate">
            <el-date-picker v-model="form.approvalDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="审批意见" prop="approvalOpinion">
            <Editor v-model="form.approvalOpinion" :height="300" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="遗留问题" prop="carryoverIssues">
            <el-input v-model="form.carryoverIssues" type="textarea" :rows="4" />
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
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import * as ProjectClosureApi from '@/api/pms/project/project-closure'
import * as ProjectApi from '@/api/pms/project/project'
import UserTag from '@/components/UserTag/index.vue'
import type { ProjectClosureVO } from '@/api/pms/project/project-closure'

defineOptions({ name: 'PmsProjectClosure' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<ProjectClosureVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  code: '',
  name: '',
  status: undefined
})
const formVisible = ref(false)
const formRef = ref()
const form = reactive<ProjectClosureVO>({ projectId: undefined!, code: '', name: '' })
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入闭环编号' }],
  name: [{ required: true, message: '请输入闭环名称' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await ProjectClosureApi.getProjectClosurePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: ProjectClosureVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: undefined,
      code: '',
      name: '',
      applicationDate: '',
      approverUserId: undefined,
      approvalDate: '',
      approvalOpinion: '',
      carryoverIssues: '',
      status: 0,
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
    form.id ? await ProjectClosureApi.updateProjectClosure(form) : await ProjectClosureApi.createProjectClosure(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: ProjectClosureVO) => {
  await message.delConfirm()
  await ProjectClosureApi.deleteProjectClosure(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (
  row: ProjectClosureVO,
  action:
    | 'submitProjectClosure'
    | 'startApproveProjectClosure'
    | 'passProjectClosure'
    | 'rejectProjectClosure'
    | 'archiveProjectClosure',
  actionText: string
) => {
  await message.confirm(`确认${actionText}项目闭环【${row.code}】？`)
  await (ProjectClosureApi as any)[action](row.id!)
  message.success(`${actionText}成功`)
  await load()
}

onMounted(load)
</script>
