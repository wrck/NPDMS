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
          class="!w-220px"
        />
      </el-form-item>
      <el-form-item label="编号" prop="code">
        <el-input v-model="query.code" clearable class="!w-180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="类型" prop="briefingType">
        <el-select v-model="query.briefingType" clearable class="!w-140px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_BRIEFING_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_BRIEFING_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openCreate()" v-hasPermi="['pms:eng-briefing:create']"
          ><Icon icon="ep:plus" />新建交底书</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无交底书数据">
      <el-table-column prop="code" label="编号" width="160" />
      <el-table-column prop="projectId" label="项目" min-width="180">
        <template #default="{ row }">
          <ProjectTag :project-id="row.projectId" />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="briefingType" label="类型" width="110">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_BRIEFING_TYPE" :value="row.briefingType" />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_BRIEFING_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="creatorUserId" label="编制人" width="120">
        <template #default="{ row }">
          <UserTag v-if="row.creatorUserId" :user-id="row.creatorUserId" />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="generateTime" label="生成时间" width="160" :formatter="dateFormatter" />
      <el-table-column prop="publishTime" label="发布时间" width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)" v-hasPermi="['pms:eng-briefing:query']"
            >明细</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0"
            @click="openEdit(row)"
            v-hasPermi="['pms:eng-briefing:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleGenerate(row)"
            v-hasPermi="['pms:eng-briefing:generate']"
            >生成</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 1"
            @click="openApprove(row)"
            v-hasPermi="['pms:eng-briefing:audit']"
            >审核</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 2"
            @click="handlePublish(row)"
            v-hasPermi="['pms:eng-briefing:publish']"
            >发布</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status !== 3 && row.status !== 4"
            @click="handleTerminate(row)"
            v-hasPermi="['pms:eng-briefing:update']"
            >作废</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 0"
            @click="remove(row)"
            v-hasPermi="['pms:eng-briefing:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 新建/编辑对话框 -->
  <Dialog v-model="formVisible" :title="form.id ? '编辑交底书' : '新建交底书'" width="960px">
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
            <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 BR-2026-001" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="类型" prop="briefingType">
            <el-select v-model="form.briefingType" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_BRIEFING_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="编制人" prop="creatorUserId">
            <PmsEntitySelect
              v-model="form.creatorUserId"
              :api="UserApi.getUserPage"
              label-field="nickname"
              value-field="id"
              query-field="nickname"
              placeholder="请选择编制人"
            />
          </el-form-item>
        </el-col>
        <el-col v-if="form.id" :span="12">
          <el-form-item label="版本号" prop="version">
            <el-input-number v-model="form.version" :min="0" class="!w-full" disabled />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="交底内容" prop="content">
            <Editor v-model="form.content" height="220px" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="附件" prop="fileUrl">
            <UploadFile v-model="form.fileUrl!" />
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
  <Dialog v-model="detailVisible" title="交底书明细" width="960px">
    <el-descriptions :column="2" border class="mb-15px">
      <el-descriptions-item label="编号">{{ current.code }}</el-descriptions-item>
      <el-descriptions-item label="名称">{{ current.name }}</el-descriptions-item>
      <el-descriptions-item label="项目">
        <ProjectTag v-if="current.projectId" :project-id="current.projectId" />
      </el-descriptions-item>
      <el-descriptions-item label="类型">
        <dict-tag :type="DICT_TYPE.PMS_BRIEFING_TYPE" :value="current.briefingType ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="状态">
        <dict-tag :type="DICT_TYPE.PMS_BRIEFING_STATUS" :value="current.status ?? ''" />
      </el-descriptions-item>
      <el-descriptions-item label="编制人">
        <UserTag v-if="current.creatorUserId" :user-id="current.creatorUserId" />
      </el-descriptions-item>
      <el-descriptions-item label="审核人">
        <UserTag v-if="current.approverUserId" :user-id="current.approverUserId" />
      </el-descriptions-item>
      <el-descriptions-item label="审核时间">{{ current.approveTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="生成时间">{{ current.generateTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="发布时间">{{ current.publishTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="文件" :span="2">
        <span v-if="current.fileName">{{ current.fileName }}（{{ current.fileSize }} 字节）</span>
        <span v-else>-</span>
      </el-descriptions-item>
      <el-descriptions-item label="交底内容" :span="2">
        <div v-html="current.content"></div>
      </el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ current.remark || '-' }}</el-descriptions-item>
      <el-descriptions-item v-if="current.approveOpinion" label="审核意见" :span="2">
        {{ current.approveOpinion }}
      </el-descriptions-item>
    </el-descriptions>
  </Dialog>

  <!-- 生成对话框 -->
  <Dialog v-model="generateVisible" title="生成交底书" width="560px">
    <el-form :model="generateForm" label-width="120px">
      <el-form-item label="交底书">{{ generateForm.code }}</el-form-item>
      <el-form-item label="模板ID" prop="templateId">
        <el-input-number v-model="generateForm.templateId" :min="0" class="!w-full" placeholder="可选，关联交底书模板ID" />
      </el-form-item>
      <el-form-item label="前序基线快照" prop="sourceSnapshot">
        <el-input v-model="generateForm.sourceSnapshot" type="textarea" :rows="4" placeholder="可选，前序基线数据快照JSON" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="generateVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="confirmGenerate">确认生成</el-button>
    </template>
  </Dialog>

  <!-- 审核对话框 -->
  <Dialog v-model="approveVisible" title="审核交底书" width="560px">
    <el-form ref="approveFormRef" :model="approveForm" :rules="approveRules" label-width="100px">
      <el-form-item label="审核动作" prop="approveAction">
        <el-radio-group v-model="approveForm.approveAction">
          <el-radio value="PASS">通过</el-radio>
          <el-radio value="REJECT">驳回</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="审核人" prop="approverUserId">
        <PmsEntitySelect
          v-model="approveForm.approverUserId"
          :api="UserApi.getUserPage"
          label-field="nickname"
          value-field="id"
          query-field="nickname"
          placeholder="请选择审核人"
        />
      </el-form-item>
      <el-form-item label="审核意见" prop="approveOpinion">
        <el-input v-model="approveForm.approveOpinion" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="approveVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="confirmApprove">确认</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as BriefingApi from '@/api/pms/engineering/briefing'
import * as ProjectApi from '@/api/pms/project/project'
import * as UserApi from '@/api/system/user'
import type { BriefingVO } from '@/api/pms/engineering/briefing'
import ProjectTag from '@/components/ProjectTag/index.vue'
import UserTag from '@/components/UserTag/index.vue'

defineOptions({ name: 'PmsEngBriefing' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<BriefingVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  code: '',
  name: '',
  briefingType: '',
  status: undefined as number | undefined
})

const load = async () => {
  loading.value = true
  try {
    const data = await BriefingApi.getBriefingPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 新建/编辑
const formVisible = ref(false)
const formRef = ref()
const form = reactive<BriefingVO>({
  projectId: undefined!,
  code: '',
  name: '',
  briefingType: 'STANDARD',
  content: '',
  fileUrl: '',
  creatorUserId: undefined,
  remark: ''
})
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入编号' }],
  name: [{ required: true, message: '请输入名称' }],
  briefingType: [{ required: true, message: '请选择类型' }]
}

const openCreate = () => {
  Object.assign(form, {
    id: undefined,
    projectId: undefined,
    code: '',
    name: '',
    briefingType: 'STANDARD',
    content: '',
    fileUrl: '',
    creatorUserId: undefined,
    remark: '',
    version: 0
  })
  formVisible.value = true
}
const openEdit = async (row: BriefingVO) => {
  const detail = await BriefingApi.getBriefing(row.id!)
  Object.assign(form, detail)
  formVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await BriefingApi.updateBriefing(form)
      message.success('更新成功')
    } else {
      await BriefingApi.createBriefing(form)
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
const current = ref<Partial<BriefingVO>>({})
const openDetail = async (row: BriefingVO) => {
  current.value = await BriefingApi.getBriefing(row.id!)
  detailVisible.value = true
}

// 生成
const generateVisible = ref(false)
const generateForm = reactive({
  id: undefined as number | undefined,
  code: '',
  templateId: undefined as number | undefined,
  sourceSnapshot: '',
  version: 0
})
const handleGenerate = (row: BriefingVO) => {
  Object.assign(generateForm, {
    id: row.id,
    code: row.code,
    templateId: row.templateId,
    sourceSnapshot: row.sourceSnapshot || '',
    version: row.version
  })
  generateVisible.value = true
}
const confirmGenerate = async () => {
  saving.value = true
  try {
    await BriefingApi.generateBriefing(generateForm as any)
    message.success('生成成功')
    generateVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// 审核
const approveVisible = ref(false)
const approveFormRef = ref()
const approveForm = reactive({
  id: undefined as number | undefined,
  approveAction: 'PASS',
  approveOpinion: '',
  approverUserId: undefined as number | undefined,
  version: 0
})
const approveRules = {
  approveAction: [{ required: true, message: '请选择审核动作' }],
  approverUserId: [{ required: true, message: '请选择审核人' }]
}
const openApprove = (row: BriefingVO) => {
  Object.assign(approveForm, {
    id: row.id,
    approveAction: 'PASS',
    approveOpinion: '',
    approverUserId: undefined,
    version: row.version
  })
  approveVisible.value = true
}
const confirmApprove = async () => {
  await approveFormRef.value.validate()
  saving.value = true
  try {
    await BriefingApi.approveBriefing(approveForm as any)
    message.success('审核完成')
    approveVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// 状态操作
const handlePublish = async (row: BriefingVO) => {
  await message.confirm('确认发布此交底书？发布后不可修改。')
  await BriefingApi.publishBriefing(row.id!)
  message.success('发布成功')
  await load()
}
const handleTerminate = async (row: BriefingVO) => {
  await message.confirm('确认作废此交底书？作废后不可恢复。')
  await BriefingApi.terminateBriefing(row.id!)
  message.success('作废成功')
  await load()
}
const remove = async (row: BriefingVO) => {
  await message.delConfirm()
  await BriefingApi.deleteBriefing(row.id!)
  message.success('删除成功')
  await load()
}

onMounted(load)
</script>
