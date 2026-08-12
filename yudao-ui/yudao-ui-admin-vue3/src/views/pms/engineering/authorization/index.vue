<template>
  <div>
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
          class="!w-220px"
        />
      </el-form-item>
      <el-form-item label="编号" prop="code">
        <el-input v-model="query.code" clearable class="!w-180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="类型" prop="authorizationType">
        <el-select v-model="query.authorizationType" clearable class="!w-140px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_AUTHORIZATION_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_APPROVAL_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openCreate()" v-hasPermi="['pms:eng-authorization:create']"
          ><Icon icon="ep:plus" />新建授权</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无授权数据">
      <el-table-column prop="code" label="编号" width="150" />
      <el-table-column prop="projectId" label="项目" min-width="160">
        <template #default="{ row }">
          <ProjectTag :project-id="row.projectId" />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="authorizationType" label="类型" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_AUTHORIZATION_TYPE" :value="row.authorizationType" />
        </template>
      </el-table-column>
      <el-table-column prop="deviceModel" label="设备型号" width="130" show-overflow-tooltip />
      <el-table-column prop="deviceSerial" label="序列号" width="130" show-overflow-tooltip />
      <el-table-column prop="licenseType" label="授权类型" width="120" show-overflow-tooltip />
      <el-table-column prop="applyStartDate" label="开始日期" width="120" />
      <el-table-column prop="applyEndDate" label="结束日期" width="120" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_APPROVAL_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)" v-hasPermi="['pms:eng-authorization:query']"
            >明细</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0 || row.status === 4 || row.status === 5"
            @click="openEdit(row)"
            v-hasPermi="['pms:eng-authorization:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0 || row.status === 4 || row.status === 5"
            @click="handleSubmit(row)"
            v-hasPermi="['pms:eng-authorization:submit']"
            >提交</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 2"
            @click="openApprove(row)"
            v-hasPermi="['pms:eng-authorization:audit']"
            >审批</el-button
          >
          <el-button
            link
            type="info"
            v-if="row.status === 1 || row.status === 2"
            @click="handleRecall(row)"
            v-hasPermi="['pms:eng-authorization:recall']"
            >撤回</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 3"
            @click="handleTerminate(row)"
            v-hasPermi="['pms:eng-authorization:terminate']"
            >终止</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 0"
            @click="remove(row)"
            v-hasPermi="['pms:eng-authorization:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 新建/编辑对话框 -->
  <Dialog v-model="formVisible" :title="form.id ? '编辑授权' : '新建授权'" width="900px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
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
          <el-form-item label="编号" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 AUTH-2026-001" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="授权类型" prop="authorizationType">
            <el-select v-model="form.authorizationType" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_AUTHORIZATION_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="设备型号" prop="deviceModel"><el-input v-model="form.deviceModel" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="设备序列号" prop="deviceSerial"><el-input v-model="form.deviceSerial" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="授权类型描述" prop="licenseType"><el-input v-model="form.licenseType" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="授权密钥" prop="licenseKey"><el-input v-model="form.licenseKey" /></el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="开始日期" prop="applyStartDate">
            <el-date-picker v-model="form.applyStartDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="结束日期" prop="applyEndDate">
            <el-date-picker v-model="form.applyEndDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="次数限制" prop="usageLimit">
            <el-input-number v-model="form.usageLimit" :min="0" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col v-if="form.id" :span="12">
          <el-form-item label="版本号" prop="version">
            <el-input-number v-model="form.version" :min="0" class="!w-full" disabled />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input v-model="form.remark" type="textarea" :rows="2" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>

  <!-- 明细对话框 -->
  <Dialog v-model="detailVisible" title="授权明细" width="900px">
    <el-descriptions :column="2" border class="mb-15px">
      <el-descriptions-item label="编号">{{ current.code }}</el-descriptions-item>
      <el-descriptions-item label="名称">{{ current.name }}</el-descriptions-item>
      <el-descriptions-item label="项目">
        <ProjectTag v-if="current.projectId" :project-id="current.projectId" />
      </el-descriptions-item>
      <el-descriptions-item label="授权类型">
        <dict-tag :type="DICT_TYPE.PMS_AUTHORIZATION_TYPE" :value="current.authorizationType" />
      </el-descriptions-item>
      <el-descriptions-item label="状态">
        <dict-tag :type="DICT_TYPE.PMS_APPROVAL_STATUS" :value="current.status" />
      </el-descriptions-item>
      <el-descriptions-item label="设备型号">{{ current.deviceModel || '-' }}</el-descriptions-item>
      <el-descriptions-item label="设备序列号">{{ current.deviceSerial || '-' }}</el-descriptions-item>
      <el-descriptions-item label="授权类型描述">{{ current.licenseType || '-' }}</el-descriptions-item>
      <el-descriptions-item label="授权密钥">{{ current.licenseKey || '-' }}</el-descriptions-item>
      <el-descriptions-item label="开始日期">{{ current.applyStartDate || '-' }}</el-descriptions-item>
      <el-descriptions-item label="结束日期">{{ current.applyEndDate || '-' }}</el-descriptions-item>
      <el-descriptions-item label="实际结束日期">{{ current.actualEndDate || '-' }}</el-descriptions-item>
      <el-descriptions-item label="次数限制">{{ current.usageLimit ?? '-' }}</el-descriptions-item>
      <el-descriptions-item label="已使用次数">{{ current.usedCount ?? 0 }}</el-descriptions-item>
      <el-descriptions-item label="提交人">
        <UserTag v-if="current.submitUserId" :user-id="current.submitUserId" />
      </el-descriptions-item>
      <el-descriptions-item label="提交时间">{{ current.submitTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="审批人">
        <UserTag v-if="current.approverUserId" :user-id="current.approverUserId" />
      </el-descriptions-item>
      <el-descriptions-item label="审批时间">{{ current.approveTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="撤回时间">{{ current.recallTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="审批意见" :span="2">{{ current.approveOpinion || '-' }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ current.remark || '-' }}</el-descriptions-item>
    </el-descriptions>
  </Dialog>

  <!-- 审批对话框 -->
  <Dialog v-model="approveVisible" title="审批授权" width="560px">
    <el-form ref="approveFormRef" :model="approveForm" :rules="approveRules" label-width="100px">
      <el-form-item label="授权编号">{{ approveForm.code }}</el-form-item>
      <el-form-item label="审批动作" prop="approveAction">
        <el-radio-group v-model="approveForm.approveAction">
          <el-radio value="PASS">通过</el-radio>
          <el-radio value="REJECT">驳回</el-radio>
          <el-radio value="TERMINATE">终止</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="审批人" prop="approverUserId">
        <PmsEntitySelect
          v-model="approveForm.approverUserId"
          :api="UserApi.getUserPage"
          label-field="nickname"
          value-field="id"
          query-field="nickname"
          placeholder="请选择审批人"
        />
      </el-form-item>
      <el-form-item label="审批意见" prop="approveOpinion">
        <el-input v-model="approveForm.approveOpinion" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="approveVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="confirmApprove">确认</el-button>
    </template>
  </Dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as AuthorizationApi from '@/api/pms/engineering/authorization'
import * as ProjectApi from '@/api/pms/project/project'
import * as UserApi from '@/api/system/user'
import type { AuthorizationVO } from '@/api/pms/engineering/authorization'
import ProjectTag from '@/components/ProjectTag/index.vue'
import UserTag from '@/components/UserTag/index.vue'

defineOptions({ name: 'PmsEngAuthorization' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<AuthorizationVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  code: '',
  name: '',
  authorizationType: '',
  status: undefined as number | undefined
})

const load = async () => {
  loading.value = true
  try {
    const data = await AuthorizationApi.getAuthorizationPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 新建/编辑
const formVisible = ref(false)
const formRef = ref()
const form = reactive<AuthorizationVO>({
  projectId: undefined,
  code: '',
  name: '',
  authorizationType: 'TEMPORARY',
  deviceModel: '',
  deviceSerial: '',
  licenseKey: '',
  licenseType: '',
  applyStartDate: '',
  applyEndDate: '',
  usageLimit: undefined,
  remark: ''
})
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入编号' }],
  name: [{ required: true, message: '请输入名称' }],
  authorizationType: [{ required: true, message: '请选择授权类型' }]
}

const openCreate = () => {
  Object.assign(form, {
    id: undefined,
    projectId: undefined,
    code: '',
    name: '',
    authorizationType: 'TEMPORARY',
    deviceModel: '',
    deviceSerial: '',
    licenseKey: '',
    licenseType: '',
    applyStartDate: '',
    applyEndDate: '',
    usageLimit: undefined,
    remark: '',
    version: 0
  })
  formVisible.value = true
}
const openEdit = async (row: AuthorizationVO) => {
  const detail = await AuthorizationApi.getAuthorization(row.id!)
  Object.assign(form, detail)
  formVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await AuthorizationApi.updateAuthorization(form)
      message.success('更新成功')
    } else {
      await AuthorizationApi.createAuthorization(form)
      message.success('创建成功')
    }
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// 明细
const detailVisible = ref(false)
const current = ref<AuthorizationVO>({})
const openDetail = async (row: AuthorizationVO) => {
  current.value = await AuthorizationApi.getAuthorization(row.id!)
  detailVisible.value = true
}

// 状态操作
const handleSubmit = async (row: AuthorizationVO) => {
  await message.confirm('确认提交此授权？提交后将进入审批流程。')
  await AuthorizationApi.submitAuthorization(row.id!)
  message.success('提交成功')
  await load()
}
const handleRecall = async (row: AuthorizationVO) => {
  await message.confirm('确认撤回此授权？')
  await AuthorizationApi.recallAuthorization(row.id!)
  message.success('撤回成功')
  await load()
}
const handleTerminate = async (row: AuthorizationVO) => {
  await message.confirm('确认终止此授权？终止后不可恢复。')
  await AuthorizationApi.terminateAuthorization(row.id!)
  message.success('终止成功')
  await load()
}
const remove = async (row: AuthorizationVO) => {
  await message.delConfirm()
  await AuthorizationApi.deleteAuthorization(row.id!)
  message.success('删除成功')
  await load()
}

// 审批
const approveVisible = ref(false)
const approveFormRef = ref()
const approveForm = reactive({
  id: undefined as number | undefined,
  code: '',
  approveAction: 'PASS',
  approverUserId: undefined as number | undefined,
  approveOpinion: '',
  version: 0
})
const approveRules = {
  approveAction: [{ required: true, message: '请选择审批动作' }],
  approverUserId: [{ required: true, message: '请选择审批人' }]
}
const openApprove = (row: AuthorizationVO) => {
  Object.assign(approveForm, {
    id: row.id,
    code: row.code,
    approveAction: 'PASS',
    approverUserId: undefined,
    approveOpinion: '',
    version: row.version
  })
  approveVisible.value = true
}
const confirmApprove = async () => {
  await approveFormRef.value.validate()
  saving.value = true
  try {
    await AuthorizationApi.approveAuthorization(approveForm as any)
    message.success('审批完成')
    approveVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
