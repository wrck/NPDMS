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
      <el-form-item label="执行编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="步骤名称" prop="stepName">
        <el-input v-model="query.stepName" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_CUTOVER_EXEC_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:cut-execution:create']"
          ><Icon icon="ep:plus" />新增执行记录</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="执行编码" min-width="140" />
      <el-table-column prop="stepName" label="步骤名称" min-width="180" />
      <el-table-column prop="operatorUserId" label="操作人" width="100" />
      <el-table-column prop="operationTime" label="操作时间" width="160" />
      <el-table-column prop="result" label="执行结果" min-width="180" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_CUTOVER_EXEC_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:cut-execution:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 0"
            @click="handleAction(row, 'startCutExecution', '开始执行')"
            v-hasPermi="['pms:cut-execution:update']"
            >开始执行</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 1"
            @click="handleAction(row, 'passCutExecution', '通过')"
            v-hasPermi="['pms:cut-execution:update']"
            >通过</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 1"
            @click="handleAction(row, 'failCutExecution', '标记失败')"
            v-hasPermi="['pms:cut-execution:update']"
            >标记失败</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 1"
            @click="handleAction(row, 'rollbackCutExecution', '回退')"
            v-hasPermi="['pms:cut-execution:update']"
            >回退</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:cut-execution:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑执行记录' : '新增执行记录'" width="780px">
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
          <el-form-item label="执行编码" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="步骤名称" prop="stepName"><el-input v-model="form.stepName" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="操作人" prop="operatorUserId"><el-input v-model="form.operatorUserId" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="操作时间" prop="operationTime">
            <el-date-picker v-model="form.operationTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="执行结果" prop="result">
            <Editor v-model="form.result" :height="300" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="异常记录" prop="exceptionRecord">
            <el-input v-model="form.exceptionRecord" type="textarea" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="证据附件" prop="evidenceUrl"><UploadFile v-model="form.evidenceUrl" /></el-form-item>
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
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import * as CutExecutionApi from '@/api/pms/cutover/cut-execution'
import * as CutTaskApi from '@/api/pms/cutover/cut-task'
import type { CutExecutionVO } from '@/api/pms/cutover/cut-execution'
import UserTag from '@/components/UserTag/index.vue'

defineOptions({ name: 'PmsCutExecution' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<CutExecutionVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, taskId: '', code: '', stepName: '', status: undefined })
const formVisible = ref(false)
const formRef = ref()
const form = reactive<CutExecutionVO>({ taskId: 0, code: '', stepName: '' })
const rules = {
  taskId: [{ required: true, message: '请输入任务编号' }],
  code: [{ required: true, message: '请输入执行编码' }],
  stepName: [{ required: true, message: '请输入步骤名称' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await CutExecutionApi.getCutExecutionPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: CutExecutionVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      taskId: 0,
      code: '',
      stepName: '',
      operatorUserId: undefined,
      operationTime: '',
      result: '',
      exceptionRecord: '',
      evidenceUrl: '',
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
    form.id ? await CutExecutionApi.updateCutExecution(form) : await CutExecutionApi.createCutExecution(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: CutExecutionVO) => {
  await message.delConfirm()
  await CutExecutionApi.deleteCutExecution(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (row: CutExecutionVO, action: string, actionText: string) => {
  await message.confirm(`确认${actionText}执行记录【${row.code}】？`)
  await (CutExecutionApi as any)[action](row.id!)
  message.success(`${actionText}成功`)
  await load()
}
onMounted(load)
</script>
