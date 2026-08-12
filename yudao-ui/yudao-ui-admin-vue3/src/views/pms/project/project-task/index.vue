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
      <el-form-item label="任务名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-220px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-160px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_TASK_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="open()" v-hasPermi="['pms:project-task:create']"
          ><Icon icon="ep:plus" />新增任务</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无任务数据">
      <el-table-column prop="projectId" label="项目编号" width="100">
        <template #default="{ row }">
          <ProjectTag :project-id="row.projectId" />
        </template>
      </el-table-column>
      <el-table-column prop="code" label="任务编码" min-width="120" />
      <el-table-column prop="name" label="任务名称" min-width="160" />
      <el-table-column prop="ownerUserId" label="负责人" width="100">
        <template #default="{ row }">
          <UserTag :user-id="row.ownerUserId" />
        </template>
      </el-table-column>
      <el-table-column prop="assigneeUserId" label="执行人" width="100">
        <template #default="{ row }">
          <UserTag :user-id="row.assigneeUserId" />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_TASK_STATUS" :value="row.status ?? 0" />
        </template>
      </el-table-column>
      <el-table-column prop="priority" label="优先级" width="90" />
      <el-table-column prop="progress" label="进度" width="90">
        <template #default="{ row }">{{ row.progress ?? 0 }}%</template>
      </el-table-column>
      <el-table-column prop="planStartTime" label="计划开始" min-width="160" :formatter="dateFormatter" />
      <el-table-column prop="planEndTime" label="计划结束" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)" v-hasPermi="['pms:project-task:update']"
            >编辑</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:project-task:delete']"
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

  <Dialog v-model="visible" :title="form.id ? '编辑任务' : '新增任务'" width="720px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="项目" prop="projectId">
        <PmsEntitySelect
          v-model="form.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择项目"
        />
      </el-form-item>
      <el-form-item label="父任务">
        <PmsEntitySelect
          v-model="form.parentId"
          :api="ProjectTaskApi.getProjectTaskPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择父任务"
        />
      </el-form-item>
      <el-form-item label="任务名称" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="任务编码"><el-input v-model="form.code" /></el-form-item>
      <el-form-item label="任务描述" :span="24">
        <Editor v-model="form.description" :height="300" />
      </el-form-item>
      <el-form-item label="负责人">
        <PmsEntitySelect
          v-model="form.ownerUserId"
          :api="UserApi.getUserPage"
          label-field="nickname"
          value-field="id"
          query-field="nickname"
          placeholder="请选择用户"
        />
      </el-form-item>
      <el-form-item label="执行人">
        <PmsEntitySelect
          v-model="form.assigneeUserId"
          :api="UserApi.getUserPage"
          label-field="nickname"
          value-field="id"
          query-field="nickname"
          placeholder="请选择用户"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="form.status" class="!w-220px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_TASK_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="优先级">
        <el-input-number v-model="form.priority" :min="0" controls-position="right" />
      </el-form-item>
      <el-form-item label="排序号">
        <el-input-number v-model="form.sort" :min="0" controls-position="right" />
      </el-form-item>
      <el-form-item label="计划开始时间">
        <el-date-picker v-model="form.planStartTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="!w-220px" />
      </el-form-item>
      <el-form-item label="计划结束时间">
        <el-date-picker v-model="form.planEndTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="!w-220px" />
      </el-form-item>
      <el-form-item label="预估工时">
        <el-input-number v-model="form.estimatedHours" :min="0" :precision="2" controls-position="right" />
      </el-form-item>
      <el-form-item label="实际工时">
        <el-input-number v-model="form.actualHours" :min="0" :precision="2" controls-position="right" />
      </el-form-item>
      <el-form-item label="进度(%)">
        <el-input-number v-model="form.progress" :min="0" :max="100" controls-position="right" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import * as ProjectTaskApi from '@/api/pms/project/project-task'
import * as ProjectApi from '@/api/pms/project/project'
import * as UserApi from '@/api/system/user'
import ProjectTag from '@/components/ProjectTag/index.vue'
import UserTag from '@/components/UserTag/index.vue'
import type { ProjectTaskVO } from '@/api/pms/project/project-task'

defineOptions({ name: 'PmsProjectTask' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<ProjectTaskVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  name: '',
  status: undefined as number | undefined
})
const visible = ref(false)
const formRef = ref()
const form = reactive<ProjectTaskVO>({
  name: '',
  status: 0
})
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  name: [{ required: true, message: '请输入任务名称' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await ProjectTaskApi.getProjectTaskPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const open = (row?: ProjectTaskVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: undefined,
      parentId: undefined,
      name: '',
      code: '',
      description: '',
      ownerUserId: undefined,
      assigneeUserId: undefined,
      status: 0,
      priority: 0,
      sort: 0,
      planStartTime: undefined,
      planEndTime: undefined,
      estimatedHours: undefined,
      actualHours: undefined,
      progress: 0
    },
    row || {}
  )
  visible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    form.id
      ? await ProjectTaskApi.updateProjectTask(form)
      : await ProjectTaskApi.createProjectTask(form)
    message.success('保存成功')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: ProjectTaskVO) => {
  await message.delConfirm()
  await ProjectTaskApi.deleteProjectTask(row.id!)
  message.success('删除成功')
  await load()
}
onMounted(load)
</script>
