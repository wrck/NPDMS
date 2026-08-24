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
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item label="验收编号" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="验收名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="验收类型" prop="acceptanceType">
        <el-select v-model="query.acceptanceType" clearable class="!w-120px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_ACCEPTANCE_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_ACCEPTANCE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:acc-acceptance:create']"
          ><Icon icon="ep:plus" />新增验收</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="验收编号" min-width="140" />
      <el-table-column prop="name" label="验收名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="acceptanceType" label="验收类型" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_ACCEPTANCE_TYPE" :value="row.acceptanceType" />
        </template>
      </el-table-column>
      <el-table-column prop="signedDate" label="签署日期" width="120" />
      <el-table-column prop="conclusion" label="验收结论" min-width="160" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_ACCEPTANCE_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="560" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:acc-acceptance:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'submitAcceptance', '提交')"
            v-hasPermi="['pms:acc-acceptance:update']"
            >提交</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 1"
            @click="handleAction(row, 'approveAcceptance', '开始审批')"
            v-hasPermi="['pms:acc-acceptance:update']"
            >开始审批</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 2"
            @click="handleAction(row, 'passAcceptance', '通过')"
            v-hasPermi="['pms:acc-acceptance:update']"
            >通过</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 2"
            @click="handleAction(row, 'rejectAcceptance', '驳回')"
            v-hasPermi="['pms:acc-acceptance:update']"
            >驳回</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 3"
            @click="handleAction(row, 'archiveAcceptance', '归档')"
            v-hasPermi="['pms:acc-acceptance:update']"
            >归档</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:acc-acceptance:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑验收' : '新增验收'" width="780px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
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
          <el-form-item label="验收编号" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="验收名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="验收类型" prop="acceptanceType">
            <el-select v-model="form.acceptanceType" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_ACCEPTANCE_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="签署日期" prop="signedDate">
            <el-date-picker v-model="form.signedDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="验收结论" prop="conclusion">
            <Editor v-model="form.conclusion" :height="300" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="审批意见" prop="opinion">
            <Editor v-model="form.opinion" :height="300" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="附件" prop="attachmentUrl">
            <UploadFile v-model="form.attachmentUrl" />
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
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as AcceptanceApi from '@/api/pms/project/acceptance'
import * as ProjectApi from '@/api/pms/project/project'
import type { AcceptanceVO } from '@/api/pms/project/acceptance'

defineOptions({ name: 'PmsAcceptance' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<AcceptanceVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  code: '',
  name: '',
  acceptanceType: undefined,
  status: undefined
})
const formVisible = ref(false)
const formRef = ref()
const form = reactive<AcceptanceVO>({ projectId: undefined!, code: '', name: '' })
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入验收编号' }],
  name: [{ required: true, message: '请输入验收名称' }],
  acceptanceType: [{ required: true, message: '请选择验收类型' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await AcceptanceApi.getAcceptancePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: AcceptanceVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: undefined,
      code: '',
      name: '',
      acceptanceType: 'PRELIMINARY',
      signedDate: '',
      conclusion: '',
      opinion: '',
      attachmentUrl: '',
      status: 0,
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
    form.id ? await AcceptanceApi.updateAcceptance(form) : await AcceptanceApi.createAcceptance(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: AcceptanceVO) => {
  await message.delConfirm()
  await AcceptanceApi.deleteAcceptance(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (
  row: AcceptanceVO,
  action:
    | 'submitAcceptance'
    | 'approveAcceptance'
    | 'passAcceptance'
    | 'rejectAcceptance'
    | 'archiveAcceptance',
  actionText: string
) => {
  await message.confirm(`确认${actionText}验收【${row.code}】？`)
  await (AcceptanceApi as any)[action](row.id!)
  message.success(`${actionText}成功`)
  await load()
}

onMounted(load)
</script>
