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
      <el-form-item label="巡检方式" prop="inspectionMode">
        <el-select v-model="query.inspectionMode" clearable class="!w-120px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_INSPECTION_MODE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_SRV_TASK_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:srv-task:create']"
          ><Icon icon="ep:plus" />新增巡检任务</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="任务编码" min-width="140" />
      <el-table-column prop="name" label="任务名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="inspectionMode" label="巡检方式" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_INSPECTION_MODE" :value="row.inspectionMode" />
        </template>
      </el-table-column>
      <el-table-column prop="sourceType" label="来源" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_SOURCE_TYPE" :value="row.sourceType" />
        </template>
      </el-table-column>
      <el-table-column prop="scheduledTime" label="计划时间" width="160" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_SRV_TASK_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="600" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:srv-task:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="info"
            v-if="row.status === 0"
            @click="handleAction(row, 'validateEquipmentAccount', '设备账号检查')"
            v-hasPermi="['pms:srv-task:update']"
            >账号检查</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'submitSrvTask', '提交计划')"
            v-hasPermi="['pms:srv-task:update']"
            >提交</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 1"
            @click="handleAction(row, 'startExecution', '开始执行')"
            v-hasPermi="['pms:srv-task:update']"
            >开始执行</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 2"
            @click="handleAction(row, 'completeExecution', '完成执行')"
            v-hasPermi="['pms:srv-task:update']"
            >完成执行</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 3"
            @click="handleAction(row, 'confirmReport', '确认报告闭环')"
            v-hasPermi="['pms:srv-task:update']"
            >确认闭环</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 2 && row.inspectionMode === 'ONLINE'"
            @click="openExecutionDlg(row)"
            v-hasPermi="['pms:srv-task:update']"
            >执行记录</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 2 && row.inspectionMode === 'OFFLINE'"
            @click="openOfflineFileDlg(row)"
            v-hasPermi="['pms:srv-task:update']"
            >离线文件</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status !== 4 && row.status !== 5"
            @click="handleAction(row, 'cancelSrvTask', '取消')"
            v-hasPermi="['pms:srv-task:update']"
            >取消</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:srv-task:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑巡检任务' : '新增巡检任务'" width="780px">
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
          <el-form-item label="设备编号" prop="equipmentId">
            <PmsEntitySelect
              v-model="form.equipmentId"
              :api="EquipmentApi.getEquipmentPage"
              :label-field="['serialNumber','name']"
              value-field="id"
              query-field="serialNumber"
              placeholder="请选择设备"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="巡检方式" prop="inspectionMode">
            <el-select v-model="form.inspectionMode" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_INSPECTION_MODE)"
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

  <!-- 在线巡检执行记录 -->
  <Dialog v-model="executionDlgVisible" :title="`执行记录 - 任务[${currentTaskCode}]`" width="960px">
    <div class="mb-10px">
      <el-button type="primary" size="small" @click="openExecutionForm()" v-hasPermi="['pms:srv-task:update']"
        ><Icon icon="ep:plus" />新增执行记录</el-button
      >
    </div>
    <el-table v-loading="executionLoading" :data="executionRows" size="small">
      <el-table-column prop="code" label="执行编码" min-width="120" />
      <el-table-column prop="ruleId" label="规则编号" width="100" />
      <el-table-column prop="executionTime" label="执行时间" width="150" />
      <el-table-column prop="executorUserId" label="执行人" width="90" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="executionStatusTagType(row.status)" size="small">
            {{ executionStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openExecutionForm(row)">编辑</el-button>
          <el-button
            link
            type="success"
            size="small"
            v-if="row.status === 0"
            @click="handleExecAction(row, 'startSrvExecution', '开始执行')"
            >开始</el-button
          >
          <el-button
            link
            type="success"
            size="small"
            v-if="row.status === 1"
            @click="handleExecAction(row, 'completeSrvExecution', '完成执行')"
            >完成</el-button
          >
          <el-button
            link
            type="warning"
            size="small"
            v-if="row.status === 0 || row.status === 1"
            @click="handleExecAction(row, 'markSrvExecutionAbnormal', '标记异常')"
            >异常</el-button
          >
          <el-button link type="danger" size="small" @click="removeExecution(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <Dialog v-model="executionFormVisible" :title="executionForm.id ? '编辑执行记录' : '新增执行记录'" width="640px" append-to-body>
      <el-form ref="executionFormRef" :model="executionForm" :rules="executionRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="执行编码" prop="code">
              <el-input v-model="executionForm.code" :disabled="!!executionForm.id" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联规则" prop="ruleId">
              <PmsEntitySelect
                v-model="executionForm.ruleId"
                :api="SrvRuleApi.getSrvRulePage"
                label-field="name"
                value-field="id"
                query-field="name"
                placeholder="请选择规则"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="执行人" prop="executorUserId">
              <el-input v-model="executionForm.executorUserId" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="执行时间" prop="executionTime">
              <el-date-picker v-model="executionForm.executionTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="!w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="执行结果" prop="result">
              <Editor v-model="executionForm.result" :height="300" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="异常记录" prop="exceptionRecord">
              <el-input v-model="executionForm.exceptionRecord" type="textarea" :rows="3" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="证据附件" prop="evidenceUrl">
              <UploadFile v-model="executionForm.evidenceUrl" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="executionForm.remark" type="textarea" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="executionFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveExecution">保存</el-button>
      </template>
    </Dialog>
  </Dialog>

  <!-- 离线巡检文件 -->
  <Dialog v-model="offlineFileDlgVisible" :title="`离线文件 - 任务[${currentTaskCode}]`" width="960px">
    <div class="mb-10px">
      <el-button type="primary" size="small" @click="openOfflineFileForm()" v-hasPermi="['pms:srv-task:update']"
        ><Icon icon="ep:plus" />新增离线文件</el-button
      >
    </div>
    <el-table v-loading="offlineFileLoading" :data="offlineFileRows" size="small">
      <el-table-column prop="code" label="文件编码" min-width="120" />
      <el-table-column prop="fileUrl" label="文件地址" min-width="200" show-overflow-tooltip />
      <el-table-column prop="fileSize" label="大小(B)" width="100" />
      <el-table-column prop="parseStatus" label="解析状态" width="100">
        <template #default="{ row }">
          <el-tag :type="parseStatusTagType(row.parseStatus)" size="small">
            {{ parseStatusLabel(row.parseStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="parsedTime" label="解析时间" width="150" />
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openOfflineFileForm(row)">编辑</el-button>
          <el-button
            link
            type="success"
            size="small"
            v-if="row.parseStatus === 0"
            @click="handleFileAction(row, 'startParseSrvOfflineFile', '开始解析')"
            >开始解析</el-button
          >
          <el-button
            link
            type="success"
            size="small"
            v-if="row.parseStatus === 1"
            @click="handleFileAction(row, 'parseSuccessSrvOfflineFile', '解析成功')"
            >解析成功</el-button
          >
          <el-button
            link
            type="warning"
            size="small"
            v-if="row.parseStatus === 1"
            @click="handleFileAction(row, 'parseFailedSrvOfflineFile', '解析失败')"
            >解析失败</el-button
          >
          <el-button link type="danger" size="small" @click="removeOfflineFile(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <Dialog v-model="offlineFileFormVisible" :title="offlineFileForm.id ? '编辑离线文件' : '新增离线文件'" width="640px" append-to-body>
      <el-form ref="offlineFileFormRef" :model="offlineFileForm" :rules="offlineFileRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="文件编码" prop="code">
              <el-input v-model="offlineFileForm.code" :disabled="!!offlineFileForm.id" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="文件大小" prop="fileSize">
              <el-input v-model="offlineFileForm.fileSize" placeholder="字节" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="文件地址" prop="fileUrl">
              <UploadFile v-model="offlineFileForm.fileUrl" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="文件校验值" prop="fileChecksum">
              <el-input v-model="offlineFileForm.fileChecksum" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="解析结果" prop="parseResult">
              <el-input v-model="offlineFileForm.parseResult" type="textarea" :rows="3" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="错误明细" prop="errorDetail">
              <el-input v-model="offlineFileForm.errorDetail" type="textarea" :rows="3" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="offlineFileForm.remark" type="textarea" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="offlineFileFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveOfflineFile">保存</el-button>
      </template>
    </Dialog>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as SrvTaskApi from '@/api/pms/service/srv-task'
import * as ProjectApi from '@/api/pms/project/project'
import * as EquipmentApi from '@/api/pms/asset/equipment'
import * as SrvRuleApi from '@/api/pms/service/srv-rule'
import type { SrvTaskVO, SrvExecutionVO, SrvOfflineFileVO } from '@/api/pms/service/srv-task'
import UserTag from '@/components/UserTag/index.vue'

defineOptions({ name: 'PmsSrvTask' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<SrvTaskVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: '',
  code: '',
  name: '',
  inspectionMode: undefined,
  status: undefined
})
const formVisible = ref(false)
const formRef = ref()
const form = reactive<SrvTaskVO>({ projectId: 0, code: '', name: '' })
const rules = {
  projectId: [{ required: true, message: '请输入项目编号' }],
  code: [{ required: true, message: '请输入任务编码' }],
  name: [{ required: true, message: '请输入任务名称' }],
  inspectionMode: [{ required: true, message: '请选择巡检方式' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await SrvTaskApi.getSrvTaskPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: SrvTaskVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: 0,
      equipmentId: undefined,
      code: '',
      name: '',
      inspectionMode: 'ONLINE',
      sourceType: 'MANUAL',
      sourceId: undefined,
      scheduledTime: '',
      actualTime: '',
      status: 0,
      accountCheckResult: '',
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
    form.id ? await SrvTaskApi.updateSrvTask(form) : await SrvTaskApi.createSrvTask(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: SrvTaskVO) => {
  await message.delConfirm()
  await SrvTaskApi.deleteSrvTask(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (
  row: SrvTaskVO,
  action: 'validateEquipmentAccount' | 'submitSrvTask' | 'startExecution' | 'completeExecution' | 'confirmReport' | 'cancelSrvTask',
  actionText: string
) => {
  await message.confirm(`确认${actionText}巡检任务【${row.code}】？`)
  await (SrvTaskApi as any)[action](row.id!)
  message.success(`${actionText}成功`)
  await load()
}

// ============================================================================
// 在线巡检执行记录（子资源）
// ============================================================================
const executionDlgVisible = ref(false)
const executionLoading = ref(false)
const executionRows = ref<SrvExecutionVO[]>([])
const currentTaskCode = ref('')
const currentTaskId = ref(0)
const executionStatusOptions = [
  { value: 0, label: '待执行' },
  { value: 1, label: '执行中' },
  { value: 2, label: '已完成' },
  { value: 3, label: '异常' }
]
const executionStatusLabel = (status: number) =>
  executionStatusOptions.find((i) => i.value === status)?.label || '未知'
const executionStatusTagType = (status: number) => {
  switch (status) {
    case 2:
      return 'success'
    case 3:
      return 'danger'
    case 1:
      return 'warning'
    default:
      return ''
  }
}
const openExecutionDlg = async (row: SrvTaskVO) => {
  currentTaskCode.value = row.code
  currentTaskId.value = row.id!
  executionDlgVisible.value = true
  await loadExecutions()
}
const loadExecutions = async () => {
  executionLoading.value = true
  try {
    const data = await SrvTaskApi.getSrvExecutionPage({
      pageNo: 1,
      pageSize: 100,
      taskId: currentTaskId.value
    } as any)
    executionRows.value = data.list
  } finally {
    executionLoading.value = false
  }
}
const executionFormVisible = ref(false)
const executionFormRef = ref()
const executionForm = reactive<SrvExecutionVO>({ taskId: 0, code: '' })
const executionRules = {
  code: [{ required: true, message: '请输入执行编码' }]
}
const openExecutionForm = (row?: SrvExecutionVO) => {
  Object.assign(
    executionForm,
    {
      id: undefined,
      taskId: currentTaskId.value,
      code: '',
      ruleId: undefined,
      executionTime: '',
      executorUserId: undefined,
      result: '',
      exceptionRecord: '',
      evidenceUrl: '',
      remark: '',
      version: undefined
    },
    row || {}
  )
  executionFormVisible.value = true
}
const saveExecution = async () => {
  await executionFormRef.value.validate()
  saving.value = true
  try {
    executionForm.id
      ? await SrvTaskApi.updateSrvExecution(executionForm)
      : await SrvTaskApi.createSrvExecution(executionForm)
    message.success('保存成功')
    executionFormVisible.value = false
    await loadExecutions()
  } finally {
    saving.value = false
  }
}
const removeExecution = async (row: SrvExecutionVO) => {
  await message.delConfirm()
  await SrvTaskApi.deleteSrvExecution(row.id!)
  message.success('删除成功')
  await loadExecutions()
}
const handleExecAction = async (
  row: SrvExecutionVO,
  action: 'startSrvExecution' | 'completeSrvExecution' | 'markSrvExecutionAbnormal',
  actionText: string
) => {
  await message.confirm(`确认${actionText}【${row.code}】？`)
  await (SrvTaskApi as any)[action](row.id!)
  message.success(`${actionText}成功`)
  await loadExecutions()
}

// ============================================================================
// 离线巡检文件（子资源）
// ============================================================================
const offlineFileDlgVisible = ref(false)
const offlineFileLoading = ref(false)
const offlineFileRows = ref<SrvOfflineFileVO[]>([])
const parseStatusOptions = [
  { value: 0, label: '待解析' },
  { value: 1, label: '解析中' },
  { value: 2, label: '解析成功' },
  { value: 3, label: '解析失败' }
]
const parseStatusLabel = (status: number) =>
  parseStatusOptions.find((i) => i.value === status)?.label || '未知'
const parseStatusTagType = (status: number) => {
  switch (status) {
    case 2:
      return 'success'
    case 3:
      return 'danger'
    case 1:
      return 'warning'
    default:
      return ''
  }
}
const openOfflineFileDlg = async (row: SrvTaskVO) => {
  currentTaskCode.value = row.code
  currentTaskId.value = row.id!
  offlineFileDlgVisible.value = true
  await loadOfflineFiles()
}
const loadOfflineFiles = async () => {
  offlineFileLoading.value = true
  try {
    const data = await SrvTaskApi.getSrvOfflineFilePage({
      pageNo: 1,
      pageSize: 100,
      taskId: currentTaskId.value
    } as any)
    offlineFileRows.value = data.list
  } finally {
    offlineFileLoading.value = false
  }
}
const offlineFileFormVisible = ref(false)
const offlineFileFormRef = ref()
const offlineFileForm = reactive<SrvOfflineFileVO>({ taskId: 0, code: '' })
const offlineFileRules = {
  code: [{ required: true, message: '请输入文件编码' }],
  fileUrl: [{ required: true, message: '请输入文件地址' }]
}
const openOfflineFileForm = (row?: SrvOfflineFileVO) => {
  Object.assign(
    offlineFileForm,
    {
      id: undefined,
      taskId: currentTaskId.value,
      code: '',
      fileUrl: '',
      fileSize: undefined,
      fileChecksum: '',
      parseStatus: 0,
      parseResult: '',
      errorDetail: '',
      parsedBy: undefined,
      parsedTime: '',
      remark: '',
      version: undefined
    },
    row || {}
  )
  offlineFileFormVisible.value = true
}
const saveOfflineFile = async () => {
  await offlineFileFormRef.value.validate()
  saving.value = true
  try {
    offlineFileForm.id
      ? await SrvTaskApi.updateSrvOfflineFile(offlineFileForm)
      : await SrvTaskApi.createSrvOfflineFile(offlineFileForm)
    message.success('保存成功')
    offlineFileFormVisible.value = false
    await loadOfflineFiles()
  } finally {
    saving.value = false
  }
}
const removeOfflineFile = async (row: SrvOfflineFileVO) => {
  await message.delConfirm()
  await SrvTaskApi.deleteSrvOfflineFile(row.id!)
  message.success('删除成功')
  await loadOfflineFiles()
}
const handleFileAction = async (
  row: SrvOfflineFileVO,
  action: 'startParseSrvOfflineFile' | 'parseSuccessSrvOfflineFile' | 'parseFailedSrvOfflineFile',
  actionText: string
) => {
  await message.confirm(`确认${actionText}【${row.code}】？`)
  await (SrvTaskApi as any)[action](row.id!)
  message.success(`${actionText}成功`)
  await loadOfflineFiles()
}

onMounted(load)
</script>
