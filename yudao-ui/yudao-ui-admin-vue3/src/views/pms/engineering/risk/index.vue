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
      <el-form-item label="类型" prop="riskType">
        <el-select v-model="query.riskType" clearable class="!w-140px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_ENG_RISK_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="等级" prop="riskLevel">
        <el-select v-model="query.riskLevel" clearable class="!w-120px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_ENG_RISK_LEVEL)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_ENG_RISK_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openCreate()" v-hasPermi="['pms:eng-risk:create']"
          ><Icon icon="ep:plus" />新建风险</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无风险数据">
      <el-table-column prop="code" label="编号" width="150" />
      <el-table-column prop="projectId" label="项目" min-width="180">
        <template #default="{ row }">
          <ProjectTag :project-id="row.projectId" />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="riskType" label="类型" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_ENG_RISK_TYPE" :value="row.riskType" />
        </template>
      </el-table-column>
      <el-table-column prop="riskLevel" label="等级" width="90">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_ENG_RISK_LEVEL" :value="row.riskLevel" />
        </template>
      </el-table-column>
      <el-table-column prop="deviceModel" label="设备型号" width="140" show-overflow-tooltip />
      <el-table-column prop="deviceSerial" label="序列号" width="140" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_ENG_RISK_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="handlerUserId" label="处理人" width="110">
        <template #default="{ row }">
          <UserTag v-if="row.handlerUserId" :user-id="row.handlerUserId" />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)" v-hasPermi="['pms:eng-risk:query']">明细</el-button>
          <el-button
            link
            type="warning"
            v-if="row.status === 0 || row.status === 1"
            @click="openEdit(row)"
            v-hasPermi="['pms:eng-risk:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 0 || row.status === 1"
            @click="openConfirm(row)"
            v-hasPermi="['pms:eng-risk:confirm']"
            >确认</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 2"
            @click="handleSyncCrm(row)"
            v-hasPermi="['pms:eng-risk:sync']"
            >同步CRM</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 3"
            @click="openClose(row)"
            v-hasPermi="['pms:eng-risk:close']"
            >关闭</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 0"
            @click="remove(row)"
            v-hasPermi="['pms:eng-risk:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 新建/编辑对话框 -->
  <Dialog v-model="formVisible" :title="form.id ? '编辑单机风险' : '新建单机风险'" width="900px">
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
            <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 RK-2026-001" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="风险类型" prop="riskType">
            <el-select v-model="form.riskType" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_ENG_RISK_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="风险等级" prop="riskLevel">
            <el-select v-model="form.riskLevel" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_ENG_RISK_LEVEL)"
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
          <el-form-item label="处理人" prop="handlerUserId">
            <PmsEntitySelect
              v-model="form.handlerUserId"
              :api="UserApi.getUserPage"
              label-field="nickname"
              value-field="id"
              query-field="nickname"
              placeholder="请选择处理人"
            />
          </el-form-item>
        </el-col>
        <el-col v-if="form.id" :span="12">
          <el-form-item label="版本号" prop="version">
            <el-input-number v-model="form.version" :min="0" class="!w-full" disabled />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="风险场景" prop="scenario">
            <Editor v-model="form.scenario" height="200px" />
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
  <Dialog v-model="detailVisible" title="单机风险明细" width="900px">
    <el-descriptions :column="2" border class="mb-15px">
      <el-descriptions-item label="编号">{{ current.code }}</el-descriptions-item>
      <el-descriptions-item label="名称">{{ current.name }}</el-descriptions-item>
      <el-descriptions-item label="项目">
        <ProjectTag v-if="current.projectId" :project-id="current.projectId" />
      </el-descriptions-item>
      <el-descriptions-item label="风险类型">
        <dict-tag :type="DICT_TYPE.PMS_ENG_RISK_TYPE" :value="current.riskType" />
      </el-descriptions-item>
      <el-descriptions-item label="风险等级">
        <dict-tag :type="DICT_TYPE.PMS_ENG_RISK_LEVEL" :value="current.riskLevel" />
      </el-descriptions-item>
      <el-descriptions-item label="状态">
        <dict-tag :type="DICT_TYPE.PMS_ENG_RISK_STATUS" :value="current.status" />
      </el-descriptions-item>
      <el-descriptions-item label="设备型号">{{ current.deviceModel || '-' }}</el-descriptions-item>
      <el-descriptions-item label="设备序列号">{{ current.deviceSerial || '-' }}</el-descriptions-item>
      <el-descriptions-item label="处理人">
        <UserTag v-if="current.handlerUserId" :user-id="current.handlerUserId" />
      </el-descriptions-item>
      <el-descriptions-item label="CRM同步">
        <el-tag v-if="current.crmSynced" type="success">已同步</el-tag>
        <el-tag v-else type="info">未同步</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="处理时间">{{ current.handleTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="CRM同步时间">{{ current.crmSyncTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="风险场景" :span="2">
        <div v-html="current.scenario"></div>
      </el-descriptions-item>
      <el-descriptions-item label="处理意见" :span="2">{{ current.handleOpinion || '-' }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ current.remark || '-' }}</el-descriptions-item>
    </el-descriptions>
  </Dialog>

  <!-- 确认/关闭对话框 -->
  <Dialog v-model="handleVisible" :title="handleTitle" width="560px">
    <el-form :model="handleForm" label-width="100px">
      <el-form-item label="风险编号">{{ handleForm.code }}</el-form-item>
      <el-form-item label="处理人" prop="handlerUserId">
        <PmsEntitySelect
          v-model="handleForm.handlerUserId"
          :api="UserApi.getUserPage"
          label-field="nickname"
          value-field="id"
          query-field="nickname"
          placeholder="请选择处理人"
        />
      </el-form-item>
      <el-form-item label="处理意见" prop="handleOpinion">
        <el-input v-model="handleForm.handleOpinion" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="confirmHandle">确认</el-button>
    </template>
  </Dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as RiskApi from '@/api/pms/engineering/risk'
import * as ProjectApi from '@/api/pms/project/project'
import * as UserApi from '@/api/system/user'
import type { RiskVO } from '@/api/pms/engineering/risk'
import ProjectTag from '@/components/ProjectTag/index.vue'
import UserTag from '@/components/UserTag/index.vue'

defineOptions({ name: 'PmsEngRisk' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<RiskVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  code: '',
  name: '',
  riskType: '',
  riskLevel: '',
  status: undefined as number | undefined
})

const load = async () => {
  loading.value = true
  try {
    const data = await RiskApi.getRiskPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 新建/编辑
const formVisible = ref(false)
const formRef = ref()
const form = reactive<RiskVO>({
  projectId: undefined,
  code: '',
  name: '',
  riskType: 'SINGLE_DEVICE',
  riskLevel: 'MEDIUM',
  deviceModel: '',
  deviceSerial: '',
  scenario: '',
  handlerUserId: undefined,
  remark: ''
})
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入编号' }],
  name: [{ required: true, message: '请输入名称' }],
  riskType: [{ required: true, message: '请选择风险类型' }],
  riskLevel: [{ required: true, message: '请选择风险等级' }]
}

const openCreate = () => {
  Object.assign(form, {
    id: undefined,
    projectId: undefined,
    code: '',
    name: '',
    riskType: 'SINGLE_DEVICE',
    riskLevel: 'MEDIUM',
    deviceModel: '',
    deviceSerial: '',
    scenario: '',
    handlerUserId: undefined,
    remark: '',
    version: 0
  })
  formVisible.value = true
}
const openEdit = async (row: RiskVO) => {
  const detail = await RiskApi.getRisk(row.id!)
  Object.assign(form, detail)
  formVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await RiskApi.updateRisk(form)
      message.success('更新成功')
    } else {
      await RiskApi.createRisk(form)
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
const current = ref<Partial<RiskVO>>({})
const openDetail = async (row: RiskVO) => {
  current.value = await RiskApi.getRisk(row.id!)
  detailVisible.value = true
}

// 确认/关闭对话框
const handleVisible = ref(false)
const handleTitle = ref('')
const handleAction = ref<'confirm' | 'close'>('confirm')
const handleForm = reactive({
  id: undefined as number | undefined,
  code: '',
  handlerUserId: undefined as number | undefined,
  handleOpinion: '',
  version: 0
})
const openConfirm = (row: RiskVO) => {
  handleAction.value = 'confirm'
  handleTitle.value = '确认风险'
  Object.assign(handleForm, {
    id: row.id,
    code: row.code,
    handlerUserId: row.handlerUserId,
    handleOpinion: '',
    version: row.version
  })
  handleVisible.value = true
}
const openClose = (row: RiskVO) => {
  handleAction.value = 'close'
  handleTitle.value = '关闭风险'
  Object.assign(handleForm, {
    id: row.id,
    code: row.code,
    handlerUserId: row.handlerUserId,
    handleOpinion: '',
    version: row.version
  })
  handleVisible.value = true
}
const confirmHandle = async () => {
  saving.value = true
  try {
    if (handleAction.value === 'confirm') {
      await RiskApi.confirmRisk(handleForm as any)
      message.success('确认成功')
    } else {
      await RiskApi.closeRisk(handleForm as any)
      message.success('关闭成功')
    }
    handleVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// 同步CRM
const handleSyncCrm = async (row: RiskVO) => {
  await message.confirm('确认将此风险同步至CRM？')
  await RiskApi.syncCrmRisk(row.id!)
  message.success('同步成功')
  await load()
}

// 删除
const remove = async (row: RiskVO) => {
  await message.delConfirm()
  await RiskApi.deleteRisk(row.id!)
  message.success('删除成功')
  await load()
}

onMounted(load)
</script>
