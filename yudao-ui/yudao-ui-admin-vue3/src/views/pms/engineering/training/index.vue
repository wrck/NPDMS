<template>
  <ContentWrap>
    <el-form :model="listQuery" inline class="-mb-15px">
      <el-form-item v-if="!props.projectId" label="项目" prop="projectId">
        <PmsEntitySelect
          v-model="listQuery.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="projectName"
          value-field="id"
          query-field="projectName"
          placeholder="请选择项目"
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="listQuery.name" clearable class="!w-180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="listQuery.status" clearable class="!w-140px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:imp-training:create']"
          ><Icon icon="ep:plus" />新建培训记录</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="name" label="培训名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="trainingTypes" label="培训类型" min-width="160">
        <template #default="{ row }">
          <el-tag
            v-for="type in (row.trainingTypes || '').split(',')"
            :key="type"
            class="mr-4px"
            size="small"
            >{{ typeLabel(type) }}</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column prop="trainingTime" label="培训时间" width="110" />
      <el-table-column prop="trainerName" label="培训工程师" width="110" />
      <el-table-column prop="contactName" label="客户联系人" width="110" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="客户确认" min-width="180">
        <template #default="{ row }">
          <span v-if="row.status === 2">
            {{ row.signConfirmerName }} {{ row.skillRating }}/{{ row.effectRating }}/{{ row.satisfactionRating }}
          </span>
          <span v-else-if="row.status === 1" class="text-gray-400">待客户确认（有效期至 {{ formatDate(row.tokenExpiresAt) }}）</span>
          <span v-else class="text-gray-400">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="330" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            v-if="row.status === 0"
            @click="openForm(row)"
            v-hasPermi="['pms:imp-training:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0 || row.status === 1"
            @click="issue(row)"
            v-hasPermi="['pms:imp-training:issue']"
            >{{ row.status === 1 ? '重新外发' : '推送至客户' }}</el-button
          >
          <el-button link type="primary" @click="downloadRecord(row)">下载培训记录表</el-button>
          <el-button
            link
            type="warning"
            v-if="row.status === 0 || row.status === 1"
            @click="voidRow(row)"
            v-hasPermi="['pms:imp-training:update']"
            >作废</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 0"
            @click="remove(row)"
            v-hasPermi="['pms:imp-training:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="listQuery.pageNo" v-model:limit="listQuery.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 新建/编辑对话框 -->
  <Dialog v-model="formVisible" :title="form.id ? '编辑培训记录' : '新建培训记录'" width="860px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item v-if="!props.projectId" label="项目" prop="projectId">
            <PmsEntitySelect
              v-model="form.projectId"
              :api="ProjectApi.getProjectPage"
              label-field="projectName"
              value-field="id"
              query-field="projectName"
              placeholder="请选择项目"
              :disabled="!!form.id"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="培训名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="客户联系人" prop="contactName">
            <el-input v-model="form.contactName" placeholder="可从项目联系人带入">
              <template #append>
                <el-button :disabled="!form.projectId" @click="pickContact">带入</el-button>
              </template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="联系电话" prop="contactPhone">
            <el-input v-model="form.contactPhone" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="培训类型" prop="trainingTypeList">
            <el-checkbox-group v-model="form.trainingTypeList">
              <el-checkbox
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_TRAINING_TYPE)"
                :key="dict.value"
                :value="dict.value"
                >{{ dict.label }}</el-checkbox
              >
            </el-checkbox-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="培训时间" prop="trainingTime">
            <el-date-picker
              v-model="form.trainingTime"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择培训时间"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="培训工程师" prop="trainerUserId">
            <PmsEntitySelect
              v-model="form.trainerUserId"
              :api="UserApi.getUserPage"
              label-field="nickname"
              value-field="id"
              query-field="nickname"
              placeholder="默认当前登录人"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="参训人数" prop="traineeCount">
            <el-input-number v-model="form.traineeCount" :min="1" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="培训内容" prop="content">
            <el-input v-model="form.content" type="textarea" :rows="4" placeholder="手动填写培训内容" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark"><el-input v-model="form.remark" /></el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>

  <!-- 外发结果对话框 -->
  <Dialog v-model="issueVisible" title="培训记录已外发" width="640px">
    <el-alert type="warning" :closable="false" :title="issueResult?.notice" />
    <el-descriptions :column="1" border class="mt-12px">
      <el-descriptions-item label="确认链接">
        <el-link type="primary" :href="publicUrl" target="_blank">{{ publicUrl }}</el-link>
      </el-descriptions-item>
      <el-descriptions-item label="令牌有效期">{{ formatDate(issueResult?.tokenExpiresAt) }}</el-descriptions-item>
      <el-descriptions-item label="培训记录表">
        <el-link v-if="issueResult?.fileUrl" type="primary" :href="issueResult?.fileUrl" target="_blank"
          >查看已生成文件</el-link
        >
      </el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button v-if="publicUrl" @click="copyLink">复制链接</el-button>
      <el-button type="primary" @click="issueVisible = false">关闭</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import * as TrainingApi from '@/api/pms/engineering/training'
import type { TrainingVO } from '@/api/pms/engineering/training'
import * as ProjectApi from '@/api/pms/project/projects'
import * as UserApi from '@/api/system/user'
import * as ContactApi from '@/api/pms/customer/contacts'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import { getTenantId } from '@/utils/auth'

defineOptions({ name: 'PmsImpTraining' })

// 项目详情页嵌入模式：传入 project-id 固定项目并隐藏项目选择；独立路由页行为不变
const props = defineProps<{ projectId?: number }>()

const message = useMessage()
const route = useRoute()

const loading = ref(false)
const rows = ref<TrainingVO[]>([])
const total = ref(0)
const listQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: props.projectId ?? (route.query.projectId ? Number(route.query.projectId) : undefined),
  name: (route.query.name as string) || '',
  status: route.query.status !== undefined ? Number(route.query.status) : (undefined as number | undefined)
})

const statusOptions = [
  { value: 0, label: '草稿' },
  { value: 1, label: '已外发' },
  { value: 2, label: '客户已确认' },
  { value: 3, label: '已作废' }
]
const statusLabel = (status?: number) => statusOptions.find((item) => item.value === status)?.label ?? ''
const statusTagType = (status?: number) =>
  status === 2 ? 'success' : status === 3 ? 'info' : status === 1 ? 'warning' : 'primary'
const typeLabel = (type: string) =>
  getStrDictOptions(DICT_TYPE.PMS_TRAINING_TYPE).find((dict) => dict.value === type)?.label ?? type

const load = async () => {
  loading.value = true
  try {
    const data = await TrainingApi.getTrainingPage(listQuery as any)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const formVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive<Record<string, any>>({})
const rules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  name: [{ required: true, message: '请填写培训名称', trigger: 'blur' }],
  trainingTypeList: [{ required: true, type: 'array', min: 1, message: '请选择培训类型', trigger: 'change' }],
  trainingTime: [{ required: true, message: '请选择培训时间', trigger: 'change' }]
}
const openForm = (row?: TrainingVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: undefined,
      name: '',
      contactName: '',
      contactPhone: '',
      trainingTypeList: [],
      trainingTime: '',
      trainerUserId: undefined,
      traineeCount: undefined,
      content: '',
      remark: '',
      version: undefined
    },
    row
      ? {
          ...row,
          trainingTypeList: (row.trainingTypes || '').split(',').filter(Boolean)
        }
      : {}
  )
  if (props.projectId) form.projectId = props.projectId
  formVisible.value = true
}
const pickContact = async () => {
  const data = await ContactApi.getProjectPage(form.projectId!, { pageNo: 1, pageSize: 50 })
  if (!data.list?.length) {
    message.warning('该项目暂无客户联系人，请手动填写')
    return
  }
  const primary = data.list.find((item: any) => item.primaryFlag) ?? data.list[0]
  form.contactName = primary.name
  form.contactPhone = primary.mobile || primary.phone || ''
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    // 后端 SaveReqVO.trainingTypes 为 List<String>，回显时再由逗号串拆分
    const { trainingTypeList, ...rest } = form
    const payload = { ...rest, trainingTypes: trainingTypeList || [] } as unknown as TrainingApi.TrainingVO
    form.id ? await TrainingApi.updateTraining(payload) : await TrainingApi.createTraining(payload)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

const issueVisible = ref(false)
const issueResult = ref<TrainingApi.TrainingIssueVO>()
const publicUrl = computed(() =>
  issueResult.value
    ? `${window.location.origin}${issueResult.value.signPath}?tenantId=${getTenantId() ?? 0}`
    : ''
)
const issue = async (row: TrainingVO) => {
  await message.confirm(
    row.status === 1
      ? '重新外发将生成新链接，原链接立即失效，确认继续？'
      : '将生成客户确认链接与培训记录表文件，确认外发？'
  )
  issueResult.value = await TrainingApi.issueTraining(row.id!)
  issueVisible.value = true
  await load()
}

const copyLink = async () => {
  await navigator.clipboard.writeText(publicUrl.value)
  message.success('链接已复制')
}

const downloadRecord = async (row: TrainingVO) => {
  const fileUrl = row.fileUrl || (await TrainingApi.generateTrainingFile(row.id!))
  window.open(fileUrl, '_blank')
  await load()
}

const voidRow = async (row: TrainingVO) => {
  await message.confirm(`确认作废培训记录【${row.name}】？作废后确认链接即刻失效。`)
  await TrainingApi.voidTraining(row.id!)
  message.success('作废成功')
  await load()
}
const remove = async (row: TrainingVO) => {
  await message.delConfirm()
  await TrainingApi.deleteTraining(row.id!)
  message.success('删除成功')
  await load()
}

onMounted(load)
</script>
