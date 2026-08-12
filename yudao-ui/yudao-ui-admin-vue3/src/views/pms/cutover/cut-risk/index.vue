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
      <el-form-item label="风险编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="风险名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="类型" prop="riskType">
        <el-select v-model="query.riskType" clearable class="!w-120px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_CUTOVER_RISK_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_CUTOVER_RISK_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:cut-risk:create']"
          ><Icon icon="ep:plus" />新增风险</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="风险编码" min-width="140" />
      <el-table-column prop="name" label="风险名称" min-width="180" />
      <el-table-column prop="riskType" label="类型" width="80">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_CUTOVER_RISK_TYPE" :value="row.riskType" />
        </template>
      </el-table-column>
      <el-table-column prop="impact" label="影响分析" min-width="180" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_CUTOVER_RISK_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="360" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:cut-risk:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'startProcessCutRisk', '开始处理')"
            v-hasPermi="['pms:cut-risk:update']"
            >开始处理</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0 || row.status === 1 || row.status === 3"
            @click="handleAction(row, 'closeCutRisk', '闭环')"
            v-hasPermi="['pms:cut-risk:update']"
            >闭环</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0 || row.status === 1"
            @click="handleAction(row, 'suspendCutRisk', '挂起')"
            v-hasPermi="['pms:cut-risk:update']"
            >挂起</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:cut-risk:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑风险' : '新增风险'" width="780px">
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
          <el-form-item label="风险编码" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="风险名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="类型" prop="riskType">
            <el-select v-model="form.riskType" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_CUTOVER_RISK_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
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
        <el-col :span="24">
          <el-form-item label="风险描述" prop="description">
            <Editor v-model="form.description" :height="300" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="影响分析" prop="impact">
            <el-input v-model="form.impact" type="textarea" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="缓解措施" prop="mitigation">
            <el-input v-model="form.mitigation" type="textarea" />
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
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as CutRiskApi from '@/api/pms/cutover/cut-risk'
import * as CutTaskApi from '@/api/pms/cutover/cut-task'
import * as UserApi from '@/api/system/user'
import type { CutRiskVO } from '@/api/pms/cutover/cut-risk'

defineOptions({ name: 'PmsCutRisk' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<CutRiskVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, taskId: '', code: '', name: '', riskType: undefined, status: undefined })
const formVisible = ref(false)
const formRef = ref()
const form = reactive<CutRiskVO>({ taskId: 0, code: '', name: '' })
const rules = {
  taskId: [{ required: true, message: '请输入任务编号' }],
  code: [{ required: true, message: '请输入风险编码' }],
  name: [{ required: true, message: '请输入风险名称' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await CutRiskApi.getCutRiskPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: CutRiskVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      taskId: 0,
      code: '',
      name: '',
      riskType: 'RISK',
      description: '',
      impact: '',
      mitigation: '',
      ownerUserId: undefined,
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
    form.id ? await CutRiskApi.updateCutRisk(form) : await CutRiskApi.createCutRisk(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: CutRiskVO) => {
  await message.delConfirm()
  await CutRiskApi.deleteCutRisk(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (row: CutRiskVO, action: string, actionText: string) => {
  await message.confirm(`确认${actionText}风险【${row.code}】？`)
  await (CutRiskApi as any)[action](row.id!)
  message.success(`${actionText}成功`)
  await load()
}
onMounted(load)
</script>
