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
      <el-form-item label="风险标题" prop="title">
        <el-input v-model="query.title" clearable class="!w-220px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="风险等级" prop="riskLevel">
        <el-select v-model="query.riskLevel" clearable class="!w-160px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_PROJECT_RISK_LEVEL)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="open()" v-hasPermi="['pms:project-risk:create']"
          ><Icon icon="ep:plus" />新增风险</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无风险数据">
      <el-table-column prop="projectId" label="项目编号" width="100">
        <template #default="{ row }">
          <ProjectTag :project-id="row.projectId" />
        </template>
      </el-table-column>
      <el-table-column prop="title" label="风险标题" min-width="160" />
      <el-table-column prop="riskLevel" label="风险等级" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_PROJECT_RISK_LEVEL" :value="row.riskLevel" />
        </template>
      </el-table-column>
      <el-table-column prop="riskType" label="风险类型" min-width="120" />
      <el-table-column prop="ownerUserId" label="负责人" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_PROJECT_RISK_STATUS" :value="row.status ?? 0" />
        </template>
      </el-table-column>
      <el-table-column prop="identifiedAt" label="识别时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column prop="closedAt" label="关闭时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)" v-hasPermi="['pms:project-risk:create']"
            >编辑</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:project-risk:create']"
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

  <Dialog v-model="visible" :title="form.id ? '编辑风险' : '新增风险'" width="720px">
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
      <el-form-item label="风险标题" prop="title"><el-input v-model="form.title" /></el-form-item>
      <el-form-item label="风险等级" prop="riskLevel">
        <el-select v-model="form.riskLevel" class="!w-220px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_PROJECT_RISK_LEVEL)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="风险类型"><el-input v-model="form.riskType" /></el-form-item>
      <el-form-item label="风险原因"><el-input v-model="form.cause" type="textarea" /></el-form-item>
      <el-form-item label="风险影响"><el-input v-model="form.impact" type="textarea" /></el-form-item>
      <el-form-item label="缓解措施"><el-input v-model="form.mitigation" type="textarea" /></el-form-item>
      <el-form-item label="应急措施"><el-input v-model="form.contingency" type="textarea" /></el-form-item>
      <el-form-item label="负责人">
        <PmsEntitySelect
          v-model="form.ownerUserId"
          :api="UserApi.getUserPage"
          label-field="nickname"
          value-field="id"
          query-field="nickname"
          placeholder="请选择负责人"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="form.status" class="!w-220px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_PROJECT_RISK_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="预警阈值"><el-input v-model="form.warningThreshold" /></el-form-item>
      <el-form-item label="识别时间">
        <el-date-picker v-model="form.identifiedAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="!w-220px" />
      </el-form-item>
      <el-form-item label="复核备注"><el-input v-model="form.reviewNotes" type="textarea" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import * as ProjectRiskApi from '@/api/pms/project/project-risk'
import * as UserApi from '@/api/system/user'
import ProjectTag from '@/components/ProjectTag/index.vue'
import UserTag from '@/components/UserTag/index.vue'
import type { ProjectRiskVO } from '@/api/pms/project/project-risk'

defineOptions({ name: 'PmsProjectRisk' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<ProjectRiskVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  title: '',
  riskLevel: '' as string
})
const visible = ref(false)
const formRef = ref()
const form = reactive<ProjectRiskVO>({
  title: '',
  riskLevel: 'MEDIUM',
  status: 0
})
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  title: [{ required: true, message: '请输入风险标题' }],
  riskLevel: [{ required: true, message: '请选择风险等级' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await ProjectRiskApi.getProjectRiskPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const open = (row?: ProjectRiskVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: undefined,
      title: '',
      riskLevel: 'MEDIUM',
      riskType: '',
      cause: '',
      impact: '',
      mitigation: '',
      contingency: '',
      ownerUserId: undefined,
      status: 0,
      warningThreshold: '',
      reviewNotes: '',
      identifiedAt: undefined
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
      ? await ProjectRiskApi.updateProjectRisk(form)
      : await ProjectRiskApi.createProjectRisk(form)
    message.success('保存成功')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: ProjectRiskVO) => {
  await message.delConfirm()
  await ProjectRiskApi.deleteProjectRisk(row.id!)
  message.success('删除成功')
  await load()
}
onMounted(load)
</script>
