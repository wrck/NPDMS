<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="模板名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-220px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="模板编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-220px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="项目类型" prop="projectType">
        <el-input v-model="query.projectType" clearable class="!w-220px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-160px">
          <el-option :value="0" label="启用" />
          <el-option :value="1" label="停用" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="open()" v-hasPermi="['pms:phase-template:create']"
          ><Icon icon="ep:plus" />新增模板</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无阶段模板数据">
      <el-table-column prop="code" label="模板编码" min-width="120" />
      <el-table-column prop="name" label="模板阶段名称" min-width="140" />
      <el-table-column prop="projectType" label="适用项目类型" min-width="120" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.COMMON_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序号" width="90" />
      <el-table-column prop="responsibleRole" label="负责角色" min-width="120" />
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
      <el-table-column prop="createTime" label="创建时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)" v-hasPermi="['pms:phase-template:create']"
            >编辑</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:phase-template:create']"
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

  <Dialog v-model="visible" :title="form.id ? '编辑阶段模板' : '新增阶段模板'" width="620px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-form-item label="模板阶段名称" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="模板阶段编码" prop="code"><el-input v-model="form.code" /></el-form-item>
      <el-form-item label="适用项目类型"><el-input v-model="form.projectType" /></el-form-item>
      <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio :value="0">启用</el-radio>
          <el-radio :value="1">停用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="排序号">
        <el-input-number v-model="form.sort" :min="0" controls-position="right" />
      </el-form-item>
      <el-form-item label="准入条件"><el-input v-model="form.entryCriteria" type="textarea" /></el-form-item>
      <el-form-item label="退出条件"><el-input v-model="form.exitCriteria" type="textarea" /></el-form-item>
      <el-form-item label="负责角色编码"><el-input v-model="form.responsibleRole" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import * as PhaseTemplateApi from '@/api/pms/project/project-phase-template'
import type { ProjectPhaseTemplateVO } from '@/api/pms/project/project-phase-template'

defineOptions({ name: 'PmsProjectPhaseTemplate' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<ProjectPhaseTemplateVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  name: '',
  code: '',
  projectType: '',
  status: undefined as number | undefined
})
const visible = ref(false)
const formRef = ref()
const form = reactive<ProjectPhaseTemplateVO>({
  name: '',
  code: '',
  status: 0
})
const rules = {
  name: [{ required: true, message: '请输入模板阶段名称' }],
  code: [{ required: true, message: '请输入模板阶段编码' }],
  status: [{ required: true, message: '请选择状态' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await PhaseTemplateApi.getProjectPhaseTemplatePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const open = (row?: ProjectPhaseTemplateVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      name: '',
      code: '',
      projectType: '',
      description: '',
      status: 0,
      sort: 0,
      entryCriteria: '',
      exitCriteria: '',
      responsibleRole: ''
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
      ? await PhaseTemplateApi.updateProjectPhaseTemplate(form)
      : await PhaseTemplateApi.createProjectPhaseTemplate(form)
    message.success('保存成功')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: ProjectPhaseTemplateVO) => {
  await message.delConfirm()
  await PhaseTemplateApi.deleteProjectPhaseTemplate(row.id!)
  message.success('删除成功')
  await load()
}
onMounted(load)
</script>
