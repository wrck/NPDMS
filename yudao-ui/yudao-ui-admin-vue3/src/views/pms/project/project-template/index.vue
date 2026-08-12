<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="模板编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="模板名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="项目类型" prop="projectType">
        <el-select v-model="query.projectType" clearable class="!w-180px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_PROJECT_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option :value="0" label="启用" />
          <el-option :value="1" label="停用" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="open()" v-hasPermi="['pms:project-template:create']">
          <Icon icon="ep:plus" />新增模板
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无项目模板数据">
      <el-table-column prop="code" label="模板编码" min-width="140" />
      <el-table-column prop="name" label="模板名称" min-width="160" />
      <el-table-column prop="projectType" label="项目类型" width="140">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_PROJECT_TYPE" :value="row.projectType" />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.COMMON_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
      <el-table-column prop="createTime" label="创建时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)" v-hasPermi="['pms:project-template:create']">编辑</el-button>
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:project-template:create']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 模板编辑 Dialog -->
  <Dialog v-model="formVisible" :title="form.id ? '编辑项目模板' : '新增项目模板'" width="960px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="模板编码" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 TPL-NET-01" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="模板名称" prop="name">
            <el-input v-model="form.name" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="项目类型" prop="projectType">
            <el-select v-model="form.projectType" clearable class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_PROJECT_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态" prop="status">
            <el-radio-group v-model="form.status">
              <el-radio :value="0">启用</el-radio>
              <el-radio :value="1">停用</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="排序号" prop="sort">
            <el-input-number v-model="form.sort" :min="0" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="描述" prop="description">
            <el-input v-model="form.description" type="textarea" :rows="2" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 阶段定义 -->
      <el-divider content-position="left">阶段定义</el-divider>
      <el-table :data="snapshot.phases" border size="small" style="margin-bottom: 8px">
        <el-table-column label="阶段编码" width="160">
          <template #default="{ row }">
            <el-input v-model="row.phaseCode" placeholder="如 STARTUP" />
          </template>
        </el-table-column>
        <el-table-column label="阶段名称" width="160">
          <template #default="{ row }">
            <el-input v-model="row.phaseName" />
          </template>
        </el-table-column>
        <el-table-column label="排序" width="80">
          <template #default="{ row }">
            <el-input-number v-model="row.sortOrder" :min="0" controls-position="right" class="!w-full" />
          </template>
        </el-table-column>
        <el-table-column label="准入条件">
          <template #default="{ row }">
            <el-input v-model="row.entryCriteria" />
          </template>
        </el-table-column>
        <el-table-column label="退出条件">
          <template #default="{ row }">
            <el-input v-model="row.exitCriteria" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index }">
            <el-button link type="danger" @click="snapshot.phases.splice($index, 1)">
              <Icon icon="ep:delete" />
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button type="primary" plain size="small" @click="snapshot.phases.push({ phaseCode: '', phaseName: '', sortOrder: snapshot.phases.length + 1, entryCriteria: '', exitCriteria: '' })">
        <Icon icon="ep:plus" />添加阶段
      </el-button>

      <!-- 任务定义 -->
      <el-divider content-position="left">任务定义</el-divider>
      <el-table :data="snapshot.tasks" border size="small" style="margin-bottom: 8px">
        <el-table-column label="任务编码" width="160">
          <template #default="{ row }">
            <el-input v-model="row.taskCode" placeholder="如 T-STARTUP-01" />
          </template>
        </el-table-column>
        <el-table-column label="任务名称" width="160">
          <template #default="{ row }">
            <el-input v-model="row.taskName" />
          </template>
        </el-table-column>
        <el-table-column label="父任务编码" width="160">
          <template #default="{ row }">
            <el-select v-model="row.parentTaskCode" clearable class="!w-full" placeholder="空=顶层">
              <el-option
                v-for="t in snapshot.tasks.filter(x => x.taskCode !== row.taskCode)"
                :key="t.taskCode"
                :label="`${t.taskCode} (${t.taskName})`"
                :value="t.taskCode"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="所属阶段" width="150">
          <template #default="{ row }">
            <el-select v-model="row.phaseCode" clearable class="!w-full">
              <el-option
                v-for="p in snapshot.phases"
                :key="p.phaseCode"
                :label="p.phaseName"
                :value="p.phaseCode"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="100">
          <template #default="{ row }">
            <el-select v-model="row.priority" class="!w-full">
              <el-option :value="0" label="低" />
              <el-option :value="1" label="中" />
              <el-option :value="2" label="高" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="排序" width="80">
          <template #default="{ row }">
            <el-input-number v-model="row.sortOrder" :min="0" controls-position="right" class="!w-full" />
          </template>
        </el-table-column>
        <el-table-column label="预估工时" width="100">
          <template #default="{ row }">
            <el-input-number v-model="row.estimatedHours" :min="0" controls-position="right" class="!w-full" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index }">
            <el-button link type="danger" @click="snapshot.tasks.splice($index, 1)">
              <Icon icon="ep:delete" />
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button type="primary" plain size="small" @click="snapshot.tasks.push({ taskCode: '', taskName: '', parentTaskCode: '', phaseCode: '', priority: 1, sortOrder: snapshot.tasks.length + 1, estimatedHours: 0, description: '' })">
        <Icon icon="ep:plus" />添加任务
      </el-button>

      <!-- 团队角色 -->
      <el-divider content-position="left">团队角色</el-divider>
      <el-table :data="snapshot.teamRoles" border size="small" style="margin-bottom: 8px">
        <el-table-column label="角色编码" width="180">
          <template #default="{ row }">
            <el-input v-model="row.roleCode" placeholder="如 PROJECT_MANAGER" />
          </template>
        </el-table-column>
        <el-table-column label="角色名称" width="180">
          <template #default="{ row }">
            <el-input v-model="row.roleName" />
          </template>
        </el-table-column>
        <el-table-column label="需求人数" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.requiredCount" :min="1" controls-position="right" class="!w-full" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index }">
            <el-button link type="danger" @click="snapshot.teamRoles.splice($index, 1)">
              <Icon icon="ep:delete" />
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button type="primary" plain size="small" @click="snapshot.teamRoles.push({ roleCode: '', roleName: '', requiredCount: 1 })">
        <Icon icon="ep:plus" />添加角色
      </el-button>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import * as TemplateApi from '@/api/pms/project/project-template'
import type { ProjectTemplateVO, TemplateSnapshot } from '@/api/pms/project/project-template'

defineOptions({ name: 'PmsProjectTemplate' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<ProjectTemplateVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  code: '',
  name: '',
  projectType: '' as string,
  status: undefined as number | undefined
})

const load = async () => {
  loading.value = true
  try {
    const data = await TemplateApi.getProjectTemplatePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 编辑表单
const formVisible = ref(false)
const formRef = ref()
const form = reactive<ProjectTemplateVO>({
  code: '',
  name: '',
  projectType: '',
  description: '',
  status: 0,
  sort: 0
})
const snapshot = reactive<TemplateSnapshot>({
  schemaVersion: 1,
  phases: [],
  tasks: [],
  teamRoles: []
})
const rules = {
  code: [{ required: true, message: '请输入模板编码' }],
  name: [{ required: true, message: '请输入模板名称' }]
}

const open = async (row?: ProjectTemplateVO) => {
  Object.assign(form, {
    id: undefined,
    code: '',
    name: '',
    projectType: '',
    description: '',
    status: 0,
    sort: 0
  })
  Object.assign(snapshot, { schemaVersion: 1, phases: [], tasks: [], teamRoles: [] })
  if (row?.id) {
    const detail = await TemplateApi.getProjectTemplate(row.id)
    Object.assign(form, detail)
    if (detail.snapshotJson) {
      Object.assign(snapshot, detail.snapshotJson)
    }
  }
  formVisible.value = true
}

const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = { ...form, snapshotJson: { ...snapshot } }
    if (form.id) {
      await TemplateApi.updateProjectTemplate(payload)
      message.success('更新成功')
    } else {
      await TemplateApi.createProjectTemplate(payload)
      message.success('创建成功')
    }
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

const remove = async (row: ProjectTemplateVO) => {
  await message.delConfirm()
  await TemplateApi.deleteProjectTemplate(row.id!)
  message.success('删除成功')
  await load()
}

onMounted(load)
</script>
