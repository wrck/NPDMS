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
      <el-form-item label="交付件编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="类型" prop="deliverableType">
        <el-select v-model="query.deliverableType" clearable class="!w-160px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_DOCUMENT_TYPE)"
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
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:eng-deliverable:create']"
          ><Icon icon="ep:plus" />新增交付件</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="交付件编码" min-width="140" />
      <el-table-column prop="name" label="交付件名称" min-width="220" show-overflow-tooltip />
      <el-table-column prop="deliverableType" label="类型" width="110">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_DOCUMENT_TYPE" :value="row.deliverableType" />
        </template>
      </el-table-column>
      <el-table-column prop="fileUrl" label="文件地址" min-width="220" show-overflow-tooltip />
      <el-table-column prop="archivedTime" label="归集时间" width="160" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_DELIVERABLE_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="340" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            v-if="row.status !== 1"
            @click="openForm(row)"
            v-hasPermi="['pms:eng-deliverable:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="archive(row)"
            v-hasPermi="['pms:eng-deliverable:archive']"
            >归集</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0 || row.status === 1"
            @click="voidRow(row)"
            v-hasPermi="['pms:eng-deliverable:update']"
            >作废</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status !== 1"
            @click="remove(row)"
            v-hasPermi="['pms:eng-deliverable:delete']"
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
          <el-form-item label="交付件编码" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="阶段编号" prop="phaseId"><el-input v-model="form.phaseId" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="类型" prop="deliverableType">
            <el-select v-model="form.deliverableType" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_DOCUMENT_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="交付件名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="来源业务类型" prop="sourceType"><el-input v-model="form.sourceType" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="来源业务编号" prop="sourceId"><el-input v-model="form.sourceId" /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="文件地址" prop="fileUrl"><UploadFile v-model="form.fileUrl" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="文件大小(B)" prop="fileSize"><el-input v-model="form.fileSize" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="文件校验值" prop="fileChecksum"><el-input v-model="form.fileChecksum" /></el-form-item>
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
import * as DeliverableApi from '@/api/pms/engineering/deliverable'
import type { DeliverableVO } from '@/api/pms/engineering/deliverable'
import * as ProjectApi from '@/api/pms/project/project'

defineOptions({ name: 'PmsEngDeliverable' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<DeliverableVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: '',
  code: '',
  deliverableType: undefined,
  status: undefined
})
const formVisible = ref(false)
const formRef = ref()
const form = reactive<DeliverableVO>({ projectId: 0, code: '', name: '', deliverableType: 'DAILY' })
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入交付件编码' }],
  name: [{ required: true, message: '请输入交付件名称' }],
  deliverableType: [{ required: true, message: '请选择类型' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await DeliverableApi.getDeliverablePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: DeliverableVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: 0,
      phaseId: undefined,
      code: '',
      name: '',
      deliverableType: 'DAILY',
      sourceType: '',
      sourceId: undefined,
      fileUrl: '',
      fileSize: undefined,
      fileChecksum: '',
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
    form.id ? await DeliverableApi.updateDeliverable(form) : await DeliverableApi.createDeliverable(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: DeliverableVO) => {
  await message.delConfirm()
  await DeliverableApi.deleteDeliverable(row.id!)
  message.success('删除成功')
  await load()
}
const archive = async (row: DeliverableVO) => {
  await message.confirm(`确认归集交付件【${row.code}】？归集后不可修改，仅可作废。`)
  await DeliverableApi.archiveDeliverable(row.id!)
  message.success('归集成功')
  await load()
}
const voidRow = async (row: DeliverableVO) => {
  await message.confirm(`确认作废交付件【${row.code}】？`)
  await DeliverableApi.voidDeliverable(row.id!)
  message.success('作废成功')
  await load()
}
onMounted(load)
</script>
