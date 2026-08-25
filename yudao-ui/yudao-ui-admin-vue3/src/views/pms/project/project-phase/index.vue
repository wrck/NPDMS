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
      <el-form-item label="阶段名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-220px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-160px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_PROJECT_PHASE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="open()" v-hasPermi="['pms:project-phase:update']"
          ><Icon icon="ep:plus" />新增阶段</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无阶段数据">
      <el-table-column prop="projectId" label="项目编号" width="100">
        <template #default="{ row }">
          <ProjectTag :project-id="row.projectId" />
        </template>
      </el-table-column>
      <el-table-column prop="code" label="阶段编码" min-width="120" />
      <el-table-column prop="name" label="阶段名称" min-width="140" />
      <el-table-column prop="sort" label="排序号" width="90" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_PROJECT_PHASE_STATUS" :value="row.status ?? 0" />
        </template>
      </el-table-column>
      <el-table-column prop="planStartTime" label="计划开始" min-width="160" :formatter="dateFormatter" />
      <el-table-column prop="planEndTime" label="计划结束" min-width="160" :formatter="dateFormatter" />
      <el-table-column prop="actualStartTime" label="实际开始" min-width="160" :formatter="dateFormatter" />
      <el-table-column prop="responsibleRole" label="负责角色" min-width="120" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)" v-hasPermi="['pms:project-phase:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            @click="openComplete(row)"
            v-hasPermi="['pms:project-phase:gate']"
            >完成阶段</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:project-phase:update']"
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

  <Dialog v-model="visible" :title="form.id ? '编辑阶段' : '新增阶段'" width="720px">
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
      <el-form-item label="阶段名称" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="阶段编码" prop="code"><el-input v-model="form.code" /></el-form-item>
      <el-form-item label="排序号">
        <el-input-number v-model="form.sort" :min="0" controls-position="right" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="form.status" class="!w-220px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_PROJECT_PHASE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="建议开始">
        <el-date-picker v-model="form.suggestedStartTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="!w-220px" />
      </el-form-item>
      <el-form-item label="建议结束">
        <el-date-picker v-model="form.suggestedEndTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="!w-220px" />
      </el-form-item>
      <el-form-item label="计划开始">
        <el-date-picker v-model="form.planStartTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="!w-220px" />
      </el-form-item>
      <el-form-item label="计划结束">
        <el-date-picker v-model="form.planEndTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="!w-220px" />
      </el-form-item>
      <el-form-item label="准入条件"><el-input v-model="form.entryCriteria" type="textarea" /></el-form-item>
      <el-form-item label="退出条件"><el-input v-model="form.exitCriteria" type="textarea" /></el-form-item>
      <el-form-item label="负责角色"><el-input v-model="form.responsibleRole" /></el-form-item>
      <el-form-item label="负责用户">
        <PmsEntitySelect
          v-model="form.responsibleUserId"
          :api="UserApi.getUserPage"
          label-field="nickname"
          value-field="id"
          query-field="nickname"
          placeholder="请选择负责用户"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>

  <Dialog v-model="completeVisible" title="完成阶段（含门禁校验）" width="520px">
    <el-form ref="completeFormRef" :model="completeForm" :rules="completeRules" label-width="120px">
      <el-form-item label="阶段" prop="phaseId">
        <PmsEntitySelect
          v-model="completeForm.phaseId"
          :api="ProjectPhaseApi.getProjectPhasePage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择阶段"
          :disabled="true"
        />
      </el-form-item>
      <el-form-item label="门禁证据"><el-input v-model="completeForm.gateEvidence" type="textarea" /></el-form-item>
      <el-form-item label="乐观锁版本号">
        <el-input-number v-model="completeForm.version" :min="0" controls-position="right" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="completeVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveComplete">完成</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import * as ProjectPhaseApi from '@/api/pms/project/project-phase'
import * as ProjectApi from '@/api/pms/project/project'
import * as UserApi from '@/api/system/user'
import type { ProjectPhaseVO, ProjectPhaseCompleteReqVO } from '@/api/pms/project/project-phase'

defineOptions({ name: 'PmsProjectPhase' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<ProjectPhaseVO[]>([])
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
const form = reactive<ProjectPhaseVO>({
  name: '',
  code: '',
  status: 0
})
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  name: [{ required: true, message: '请输入阶段名称' }],
  code: [{ required: true, message: '请输入阶段编码' }]
}

const completeVisible = ref(false)
const completeFormRef = ref()
const completeForm = reactive<ProjectPhaseCompleteReqVO>({
  phaseId: undefined!,
  gateEvidence: '',
  version: 0
})
const completeRules = {
  phaseId: [{ required: true, message: '请选择阶段' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await ProjectPhaseApi.getProjectPhasePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const open = (row?: ProjectPhaseVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: undefined,
      templateId: undefined,
      name: '',
      code: '',
      sort: 0,
      status: 0,
      suggestedStartTime: undefined,
      suggestedEndTime: undefined,
      planStartTime: undefined,
      planEndTime: undefined,
      actualStartTime: undefined,
      actualEndTime: undefined,
      deviationReason: '',
      entryCriteria: '',
      exitCriteria: '',
      responsibleRole: '',
      responsibleUserId: undefined
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
      ? await ProjectPhaseApi.updateProjectPhase(form)
      : await ProjectPhaseApi.createProjectPhase(form)
    message.success('保存成功')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: ProjectPhaseVO) => {
  await message.delConfirm()
  await ProjectPhaseApi.deleteProjectPhase(row.id!)
  message.success('删除成功')
  await load()
}
const openComplete = (row: ProjectPhaseVO) => {
  Object.assign(completeForm, {
    phaseId: row.id,
    gateEvidence: '',
    version: row.version ?? 0
  })
  completeVisible.value = true
}
const saveComplete = async () => {
  await completeFormRef.value.validate()
  saving.value = true
  try {
    await ProjectPhaseApi.completeProjectPhase(completeForm)
    message.success('阶段已完成')
    completeVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
