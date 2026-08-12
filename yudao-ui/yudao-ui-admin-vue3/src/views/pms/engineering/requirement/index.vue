<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="项目编号" prop="projectId">
        <PmsEntitySelect
          v-model="query.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择项目"
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item label="需求编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="需求名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="需求类型" prop="requirementType">
        <el-select v-model="query.requirementType" clearable class="!w-160px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_REQUIREMENT_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_REQUIREMENT_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:eng-requirement:create']"
          ><Icon icon="ep:plus" />新增需求</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="需求编码" min-width="140" />
      <el-table-column prop="name" label="需求名称" min-width="180" />
      <el-table-column prop="requirementType" label="类型" width="110">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_REQUIREMENT_TYPE" :value="row.requirementType" />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_REQUIREMENT_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="340" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:eng-requirement:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'submit')"
            v-hasPermi="['pms:eng-requirement:update']"
            >提交</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 1"
            @click="handleAction(row, 'markEffective')"
            v-hasPermi="['pms:eng-requirement:update']"
            >标记生效</el-button
          >
          <el-button
            link
            type="info"
            v-if="row.status === 2"
            @click="handleAction(row, 'archive')"
            v-hasPermi="['pms:eng-requirement:update']"
            >归档</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:eng-requirement:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑需求' : '新增需求'" width="820px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="项目编号" prop="projectId">
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
          <el-form-item label="需求编码" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="需求名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="需求类型" prop="requirementType">
            <el-select v-model="form.requirementType" :disabled="!!form.id" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_REQUIREMENT_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="需求背景" prop="background">
            <Editor v-model="form.background" height="200px" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="拓扑描述" prop="topology"><el-input v-model="form.topology" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="传输需求" prop="transmission"><el-input v-model="form.transmission" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="流量特征" prop="traffic"><el-input v-model="form.traffic" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="业务描述" prop="business"><el-input v-model="form.business" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="IP 规划" prop="ipPlan"><el-input v-model="form.ipPlan" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="冗余设计" prop="redundancy"><el-input v-model="form.redundancy" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="防护要求" prop="protection"><el-input v-model="form.protection" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="运维要求" prop="oAndM"><el-input v-model="form.oAndM" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="日志留存" prop="logRetention"><el-input v-model="form.logRetention" /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="接口内容" prop="interfaceContent">
            <Editor v-model="form.interfaceContent" height="200px" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input v-model="form.remark" type="textarea" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import * as RequirementApi from '@/api/pms/engineering/requirement'
import type { RequirementVO } from '@/api/pms/engineering/requirement'
import * as ProjectApi from '@/api/pms/project/project'

defineOptions({ name: 'PmsEngRequirement' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<RequirementVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: '',
  code: '',
  name: '',
  requirementType: '',
  status: undefined
})
const formVisible = ref(false)
const formRef = ref()
const form = reactive<RequirementVO>({ projectId: 0, code: '', name: '', requirementType: 'BUSINESS' })
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入需求编码' }],
  name: [{ required: true, message: '请输入需求名称' }],
  requirementType: [{ required: true, message: '请选择需求类型' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await RequirementApi.getRequirementPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: RequirementVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: 0,
      code: '',
      name: '',
      requirementType: 'BUSINESS',
      background: '',
      topology: '',
      transmission: '',
      traffic: '',
      business: '',
      ipPlan: '',
      redundancy: '',
      protection: '',
      oAndM: '',
      logRetention: '',
      interfaceContent: '',
      remark: '',
      version: undefined
    },
    row || {}
  )
  formVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    form.id ? await RequirementApi.updateRequirement(form) : await RequirementApi.createRequirement(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: RequirementVO) => {
  await message.delConfirm()
  await RequirementApi.deleteRequirement(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (row: RequirementVO, action: 'submit' | 'markEffective' | 'archive') => {
  const actionText = { submit: '提交', markEffective: '标记生效', archive: '归档' }[action]
  await message.confirm(`确认${actionText}需求【${row.code}】？`)
  if (action === 'submit') await RequirementApi.submitRequirement(row.id!)
  if (action === 'markEffective') await RequirementApi.markEffectiveRequirement(row.id!)
  if (action === 'archive') await RequirementApi.archiveRequirement(row.id!)
  message.success(`${actionText}成功`)
  await load()
}
onMounted(load)
</script>
