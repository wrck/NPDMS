<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="任务编号" prop="taskId">
        <PmsEntitySelect
          v-model="query.taskId"
          :api="SrvTaskApi.getSrvTaskPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择巡检任务"
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item label="问题编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="问题名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="严重程度" prop="severity">
        <el-select v-model="query.severity" clearable class="!w-120px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_SRV_ISSUE_SEVERITY)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_SRV_ISSUE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:srv-issue:create']"
          ><Icon icon="ep:plus" />新增问题</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="问题编码" min-width="140" />
      <el-table-column prop="name" label="问题名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="severity" label="严重程度" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_SRV_ISSUE_SEVERITY" :value="row.severity" />
        </template>
      </el-table-column>
      <el-table-column prop="ownerUserId" label="责任人" width="100" />
      <el-table-column prop="deadline" label="整改截止" width="160" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_SRV_ISSUE_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="420" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:srv-issue:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="openAssign(row)"
            v-hasPermi="['pms:srv-issue:update']"
            >分派</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 1"
            @click="openAction(row, 'resolve')"
            v-hasPermi="['pms:srv-issue:update']"
            >整改完成</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 2"
            @click="openAction(row, 'verify')"
            v-hasPermi="['pms:srv-issue:update']"
            >验证关闭</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:srv-issue:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑巡检问题' : '新增巡检问题'" width="780px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="任务编号" prop="taskId">
            <PmsEntitySelect
              v-model="form.taskId"
              :api="SrvTaskApi.getSrvTaskPage"
              label-field="name"
              value-field="id"
              query-field="name"
              placeholder="请选择巡检任务"
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
          <el-form-item label="问题名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="严重程度" prop="severity">
            <el-select v-model="form.severity" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_SRV_ISSUE_SEVERITY)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="问题描述" prop="description">
            <Editor v-model="form.description" :height="300" />
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

  <Dialog v-model="assignVisible" title="分派问题" width="540px">
    <el-form ref="assignFormRef" :model="assignForm" :rules="assignRules" label-width="120px">
      <el-form-item label="责任人" prop="ownerUserId">
        <PmsEntitySelect
          v-model="assignForm.ownerUserId"
          :api="UserApi.getUserPage"
          label-field="nickname"
          value-field="id"
          query-field="nickname"
          placeholder="请选择用户"
        />
      </el-form-item>
      <el-form-item label="整改截止时间" prop="deadline">
        <el-date-picker v-model="assignForm.deadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="!w-full" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="assignVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitAssign">确认分派</el-button>
    </template>
  </Dialog>

  <Dialog v-model="actionVisible" :title="actionForm.action === 'resolve' ? '整改完成' : '验证关闭'" width="540px">
    <el-form :model="actionForm" label-width="120px">
      <el-form-item v-if="actionForm.action === 'resolve'" label="整改方案" required>
        <Editor v-model="actionForm.solution" :height="300" />
      </el-form-item>
      <el-form-item v-else label="验证结果" required>
        <el-input v-model="actionForm.verifyResult" type="textarea" :rows="4" placeholder="请输入验证结果" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="actionVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitAction">确认</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as SrvIssueApi from '@/api/pms/service/srv-issue'
import * as SrvTaskApi from '@/api/pms/service/srv-task'
import * as UserApi from '@/api/system/user'
import type { SrvIssueVO } from '@/api/pms/service/srv-issue'
import UserTag from '@/components/UserTag/index.vue'

defineOptions({ name: 'PmsSrvIssue' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<SrvIssueVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  taskId: '',
  code: '',
  name: '',
  severity: undefined,
  status: undefined
})
const formVisible = ref(false)
const formRef = ref()
const form = reactive<SrvIssueVO>({ taskId: 0, code: '', name: '' })
const rules = {
  taskId: [{ required: true, message: '请输入任务编号' }],
  code: [{ required: true, message: '请输入问题编码' }],
  name: [{ required: true, message: '请输入问题名称' }],
  severity: [{ required: true, message: '请选择严重程度' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await SrvIssueApi.getSrvIssuePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: SrvIssueVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      taskId: 0,
      code: '',
      name: '',
      description: '',
      severity: 'M',
      ownerUserId: undefined,
      deadline: '',
      solution: '',
      verifyResult: '',
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
    form.id ? await SrvIssueApi.updateSrvIssue(form) : await SrvIssueApi.createSrvIssue(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: SrvIssueVO) => {
  await message.delConfirm()
  await SrvIssueApi.deleteSrvIssue(row.id!)
  message.success('删除成功')
  await load()
}

// 分派
const assignVisible = ref(false)
const assignFormRef = ref()
const assignForm = reactive({ id: 0, ownerUserId: undefined as number | undefined, deadline: '', version: undefined as number | undefined })
const assignRules = {
  ownerUserId: [{ required: true, message: '请输入责任人' }]
}
const openAssign = (row: SrvIssueVO) => {
  assignForm.id = row.id!
  assignForm.ownerUserId = undefined
  assignForm.deadline = ''
  assignForm.version = row.version
  assignVisible.value = true
}
const submitAssign = async () => {
  await assignFormRef.value.validate()
  saving.value = true
  try {
    await SrvIssueApi.assignIssue({
      id: assignForm.id,
      ownerUserId: assignForm.ownerUserId!,
      deadline: assignForm.deadline,
      version: assignForm.version
    })
    message.success('分派成功')
    assignVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// 整改 / 验证
const actionVisible = ref(false)
const actionForm = reactive({
  id: 0,
  solution: '',
  verifyResult: '',
  action: 'resolve' as 'resolve' | 'verify',
  version: undefined as number | undefined
})
const openAction = (row: SrvIssueVO, action: 'resolve' | 'verify') => {
  actionForm.id = row.id!
  actionForm.solution = ''
  actionForm.verifyResult = ''
  actionForm.action = action
  actionForm.version = row.version
  actionVisible.value = true
}
const submitAction = async () => {
  if (actionForm.action === 'resolve' && !actionForm.solution) {
    message.warning('请输入整改方案')
    return
  }
  if (actionForm.action === 'verify' && !actionForm.verifyResult) {
    message.warning('请输入验证结果')
    return
  }
  saving.value = true
  try {
    if (actionForm.action === 'resolve') {
      await SrvIssueApi.resolveIssue({
        id: actionForm.id,
        solution: actionForm.solution,
        version: actionForm.version
      })
    } else {
      await SrvIssueApi.verifyIssue({
        id: actionForm.id,
        verifyResult: actionForm.verifyResult,
        version: actionForm.version
      })
    }
    message.success('操作成功')
    actionVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
