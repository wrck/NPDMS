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
      <el-form-item label="报告编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="报告类型" prop="reportType">
        <el-select v-model="query.reportType" clearable class="!w-140px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_SRV_REPORT_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_SRV_REPORT_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:srv-report:create']"
          ><Icon icon="ep:plus" />新增报告</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="报告编码" min-width="140" />
      <el-table-column prop="taskId" label="任务编号" width="120" />
      <el-table-column prop="reportType" label="类型" width="120">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_SRV_REPORT_TYPE" :value="row.reportType" />
        </template>
      </el-table-column>
      <el-table-column prop="generatedBy" label="生成人" width="100" />
      <el-table-column prop="generatedTime" label="生成时间" width="160" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_SRV_REPORT_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:srv-report:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'generateSrvReport', '生成')"
            v-hasPermi="['pms:srv-report:update']"
            >生成</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 1"
            @click="handleAction(row, 'archiveSrvReport', '归档')"
            v-hasPermi="['pms:srv-report:update']"
            >归档</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:srv-report:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑巡检报告' : '新增巡检报告'" width="780px">
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
          <el-form-item label="报告编码" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="报告类型" prop="reportType">
            <el-select v-model="form.reportType" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_SRV_REPORT_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="报告内容" prop="content">
            <Editor v-model="form.content" :height="300" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="巡检快照" prop="snapshot">
            <el-input v-model="form.snapshot" type="textarea" :rows="4" />
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
import * as SrvReportApi from '@/api/pms/service/srv-report'
import * as SrvTaskApi from '@/api/pms/service/srv-task'
import type { SrvReportVO } from '@/api/pms/service/srv-report'

defineOptions({ name: 'PmsSrvReport' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<SrvReportVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  taskId: '',
  code: '',
  reportType: undefined,
  status: undefined
})
const formVisible = ref(false)
const formRef = ref()
const form = reactive<SrvReportVO>({ taskId: 0, code: '' })
const rules = {
  taskId: [{ required: true, message: '请输入任务编号' }],
  code: [{ required: true, message: '请输入报告编码' }],
  reportType: [{ required: true, message: '请选择报告类型' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await SrvReportApi.getSrvReportPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: SrvReportVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      taskId: 0,
      code: '',
      reportType: 'STANDARD',
      content: '',
      snapshot: '',
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
    form.id ? await SrvReportApi.updateSrvReport(form) : await SrvReportApi.createSrvReport(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: SrvReportVO) => {
  await message.delConfirm()
  await SrvReportApi.deleteSrvReport(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (row: SrvReportVO, action: 'generateSrvReport' | 'archiveSrvReport', actionText: string) => {
  await message.confirm(`确认${actionText}报告【${row.code}】？`)
  await (SrvReportApi as any)[action](row.id!)
  message.success(`${actionText}成功`)
  await load()
}
onMounted(load)
</script>
