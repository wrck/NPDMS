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
      <el-form-item label="交付件编号" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="交付件名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="交付件类型" prop="deliverableType">
        <el-select v-model="query.deliverableType" clearable class="!w-120px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_DELIVERABLE_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_DELIVERABLE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:acc-deliverable-checklist:create']"
          ><Icon icon="ep:plus" />新增交付件</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="交付件编号" min-width="140" />
      <el-table-column prop="name" label="交付件名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="deliverableType" label="类型" width="90">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_DELIVERABLE_TYPE" :value="row.deliverableType" />
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="90" />
      <el-table-column prop="signedFlag" label="已签章" width="90">
        <template #default="{ row }">
          <el-tag :type="row.signedFlag ? 'success' : 'info'" size="small">
            {{ row.signedFlag ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="validFlag" label="有效" width="90">
        <template #default="{ row }">
          <el-tag :type="row.validFlag ? 'success' : 'danger'" size="small">
            {{ row.validFlag ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="submittedDate" label="提交日期" width="120" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_DELIVERABLE_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="500" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:acc-deliverable-checklist:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'submitDeliverableChecklist', '提交')"
            v-hasPermi="['pms:acc-deliverable-checklist:update']"
            >提交</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 1"
            @click="handleAction(row, 'passDeliverableChecklist', '通过')"
            v-hasPermi="['pms:acc-deliverable-checklist:update']"
            >通过</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 1"
            @click="handleAction(row, 'rejectDeliverableChecklist', '驳回')"
            v-hasPermi="['pms:acc-deliverable-checklist:update']"
            >驳回</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:acc-deliverable-checklist:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑交付件' : '新增交付件'" width="780px">
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
          <el-form-item label="交付件编号" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="交付件名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="交付件类型" prop="deliverableType">
            <el-select v-model="form.deliverableType" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_DELIVERABLE_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="版本" prop="version"><el-input v-model="form.version" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="提交日期" prop="submittedDate">
            <el-date-picker v-model="form.submittedDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="是否签章" prop="signedFlag">
            <el-switch v-model="form.signedFlag" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="是否有效" prop="validFlag">
            <el-switch v-model="form.validFlag" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="附件" prop="attachmentUrl">
            <UploadFile v-model="form.attachmentUrl!" />
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
import * as DeliverableChecklistApi from '@/api/pms/project/deliverable-checklist'
import * as ProjectApi from '@/api/pms/project/project'
import type { DeliverableChecklistVO } from '@/api/pms/project/deliverable-checklist'

defineOptions({ name: 'PmsDeliverableChecklist' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<DeliverableChecklistVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  code: '',
  name: '',
  deliverableType: undefined,
  status: undefined
})
const formVisible = ref(false)
const formRef = ref()
const form = reactive<DeliverableChecklistVO>({ projectId: undefined!, code: '', name: '' })
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入交付件编号' }],
  name: [{ required: true, message: '请输入交付件名称' }],
  deliverableType: [{ required: true, message: '请选择交付件类型' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await DeliverableChecklistApi.getDeliverableChecklistPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: DeliverableChecklistVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: undefined,
      code: '',
      name: '',
      deliverableType: 'REQUIRED',
      version: '',
      signedFlag: false,
      validFlag: true,
      submittedDate: '',
      attachmentUrl: '',
      status: 0,
      remark: '',
      versionNum: undefined
    },
    row || {}
  )
  formVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    form.id
      ? await DeliverableChecklistApi.updateDeliverableChecklist(form)
      : await DeliverableChecklistApi.createDeliverableChecklist(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: DeliverableChecklistVO) => {
  await message.delConfirm()
  await DeliverableChecklistApi.deleteDeliverableChecklist(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (
  row: DeliverableChecklistVO,
  action: 'submitDeliverableChecklist' | 'passDeliverableChecklist' | 'rejectDeliverableChecklist',
  actionText: string
) => {
  await message.confirm(`确认${actionText}交付件【${row.code}】？`)
  await (DeliverableChecklistApi as any)[action](row.id!)
  message.success(`${actionText}成功`)
  await load()
}

onMounted(load)
</script>
