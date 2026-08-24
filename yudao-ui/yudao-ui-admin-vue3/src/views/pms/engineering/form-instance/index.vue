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
      <el-form-item label="模板" prop="templateId">
        <el-select v-model="query.templateId" clearable filterable class="!w-220px" placeholder="请选择模板">
          <el-option v-for="item in templateOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="编号" prop="code">
        <el-input v-model="query.code" clearable class="!w-180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_FORM_INSTANCE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openCreate()" v-hasPermi="['pms:eng-form-instance:create']"
          ><Icon icon="ep:plus" />新建实例</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无表单实例数据">
      <el-table-column prop="code" label="编号" width="160" />
      <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="projectId" label="项目" min-width="180">
        <template #default="{ row }">
          <ProjectTag :project-id="row.projectId" />
        </template>
      </el-table-column>
      <el-table-column prop="templateId" label="模板" min-width="160">
        <template #default="{ row }">
          <span>{{ templateLabel(row.templateId) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_FORM_INSTANCE_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="fillerUserId" label="填报人" width="120">
        <template #default="{ row }">
          <UserTag v-if="row.fillerUserId" :user-id="row.fillerUserId" />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="submitTime" label="提交时间" width="160" :formatter="dateFormatter" />
      <el-table-column prop="createTime" label="创建时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)" v-hasPermi="['pms:eng-form-instance:query']"
            >明细</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0 || row.status === 1 || row.status === 4"
            @click="openEdit(row)"
            v-hasPermi="['pms:eng-form-instance:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0 || row.status === 1 || row.status === 4"
            @click="handleSubmit(row)"
            v-hasPermi="['pms:eng-form-instance:submit']"
            >提交</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 2"
            @click="openApprove(row)"
            v-hasPermi="['pms:eng-form-instance:audit']"
            >审核</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 0 || row.status === 1 || row.status === 4"
            @click="remove(row)"
            v-hasPermi="['pms:eng-form-instance:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 新建/编辑对话框 -->
  <Dialog v-model="formVisible" :title="form.id ? '编辑表单实例' : '新建表单实例'" width="960px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="编号" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 FI-2026-001" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="名称" prop="name"><el-input v-model="form.name" placeholder="如 XX项目防火墙采集表" /></el-form-item>
        </el-col>
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
          <el-form-item label="模板" prop="templateId">
            <el-select v-model="form.templateId" filterable class="!w-full" :disabled="!!form.id" placeholder="请选择已发布模板">
              <el-option v-for="item in templateOptions" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="填报人" prop="fillerUserId">
            <PmsEntitySelect
              v-model="form.fillerUserId"
              :api="UserApi.getUserPage"
              label-field="nickname"
              value-field="id"
              query-field="nickname"
              placeholder="请选择填报人"
            />
          </el-form-item>
        </el-col>
        <el-col v-if="form.id" :span="12">
          <el-form-item label="版本号" prop="version">
            <el-input-number v-model="form.version" :min="0" class="!w-full" disabled />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="填报数据" prop="formData">
            <el-input v-model="form.formData" type="textarea" :rows="8" placeholder='请输入填报数据JSON，如 {"deviceModel":"NGFW-5000","deployMode":"transparent"}' />
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
  <Dialog v-model="detailVisible" title="表单实例明细" width="960px">
    <el-descriptions :column="2" border class="mb-15px">
      <el-descriptions-item label="编号">{{ current.code }}</el-descriptions-item>
      <el-descriptions-item label="名称">{{ current.name || '-' }}</el-descriptions-item>
      <el-descriptions-item label="项目">
        <ProjectTag v-if="current.projectId" :project-id="current.projectId" />
      </el-descriptions-item>
      <el-descriptions-item label="模板">{{ templateLabel(current.templateId) }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <dict-tag :type="DICT_TYPE.PMS_FORM_INSTANCE_STATUS" :value="current.status" />
      </el-descriptions-item>
      <el-descriptions-item label="版本号">{{ current.version }}</el-descriptions-item>
      <el-descriptions-item label="填报人">
        <UserTag v-if="current.fillerUserId" :user-id="current.fillerUserId" />
      </el-descriptions-item>
      <el-descriptions-item label="审核人">
        <UserTag v-if="current.approverUserId" :user-id="current.approverUserId" />
      </el-descriptions-item>
      <el-descriptions-item label="提交时间">{{ current.submitTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="审核时间">{{ current.approveTime || '-' }}</el-descriptions-item>
      <el-descriptions-item label="模板快照" :span="2">
        <pre class="whitespace-pre-wrap break-all max-h-200px overflow-auto">{{ current.templateSnapshot }}</pre>
      </el-descriptions-item>
      <el-descriptions-item label="填报数据" :span="2">
        <pre class="whitespace-pre-wrap break-all max-h-200px overflow-auto">{{ current.formData }}</pre>
      </el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ current.remark || '-' }}</el-descriptions-item>
      <el-descriptions-item v-if="current.approveOpinion" label="审核意见" :span="2">
        {{ current.approveOpinion }}
      </el-descriptions-item>
    </el-descriptions>
  </Dialog>

  <!-- 审核对话框 -->
  <Dialog v-model="approveVisible" title="审核表单实例" width="560px">
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
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import * as FormInstanceApi from '@/api/pms/engineering/form-instance'
import * as FormTemplateApi from '@/api/pms/engineering/form-template'
import * as ProjectApi from '@/api/pms/project/project'
import * as UserApi from '@/api/system/user'
import type { FormInstanceVO } from '@/api/pms/engineering/form-instance'
import type { FormTemplateVO } from '@/api/pms/engineering/form-template'
import ProjectTag from '@/components/ProjectTag/index.vue'
import UserTag from '@/components/UserTag/index.vue'

defineOptions({ name: 'PmsEngFormInstance' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<FormInstanceVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  templateId: undefined as number | undefined,
  code: '',
  name: '',
  status: undefined as number | undefined
})

const templateOptions = ref<FormTemplateVO[]>([])

const templateLabel = (id?: number) => {
  if (!id) return '-'
  const t = templateOptions.value.find((i) => i.id === id)
  return t ? t.name : `模板#${id}`
}

const loadTemplates = async () => {
  try {
    templateOptions.value = await FormTemplateApi.getPublishedFormTemplateList()
  } catch (e) {
    templateOptions.value = []
  }
}

const load = async () => {
  loading.value = true
  try {
    const data = await FormInstanceApi.getFormInstancePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 新建/编辑
const formVisible = ref(false)
const formRef = ref()
const form = reactive<FormInstanceVO>({
  code: '',
  projectId: undefined,
  templateId: undefined,
  name: '',
  formData: '',
  fillerUserId: undefined,
  remark: '',
  version: 0
})
const rules = {
  code: [{ required: true, message: '请输入编号' }],
  projectId: [{ required: true, message: '请选择项目' }],
  templateId: [{ required: true, message: '请选择模板' }]
}

const openCreate = () => {
  Object.assign(form, {
    id: undefined,
    code: '',
    projectId: undefined,
    templateId: undefined,
    name: '',
    formData: '',
    fillerUserId: undefined,
    remark: '',
    version: 0
  })
  formVisible.value = true
}
const openEdit = async (row: FormInstanceVO) => {
  const detail = await FormInstanceApi.getFormInstance(row.id!)
  Object.assign(form, detail)
  formVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await FormInstanceApi.updateFormInstance(form)
      message.success('更新成功')
    } else {
      await FormInstanceApi.createFormInstance(form)
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
const current = ref<Partial<FormInstanceVO>>({})
const openDetail = async (row: FormInstanceVO) => {
  current.value = await FormInstanceApi.getFormInstance(row.id!)
  detailVisible.value = true
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
const openApprove = (row: FormInstanceVO) => {
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
    await FormInstanceApi.approveFormInstance(approveForm as any)
    message.success('审核完成')
    approveVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// 状态操作
const handleSubmit = async (row: FormInstanceVO) => {
  await message.confirm('确认提交此表单实例？提交后将进入审核流程。')
  await FormInstanceApi.submitFormInstance(row.id!)
  message.success('提交成功')
  await load()
}
const remove = async (row: FormInstanceVO) => {
  await message.delConfirm()
  await FormInstanceApi.deleteFormInstance(row.id!)
  message.success('删除成功')
  await load()
}

onMounted(() => {
  loadTemplates()
  load()
})
</script>
