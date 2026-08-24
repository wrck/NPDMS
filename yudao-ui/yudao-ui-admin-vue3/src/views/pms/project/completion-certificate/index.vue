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
      <el-form-item label="证明编号" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="证明名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_COMPLETION_CERT_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:acc-completion-certificate:create']"
          ><Icon icon="ep:plus" />新增完工证明</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="证明编号" min-width="140" />
      <el-table-column prop="name" label="证明名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="customerId" label="客户编号" width="100" />
      <el-table-column prop="certificateNo" label="证书编号" min-width="140" />
      <el-table-column prop="signedDate" label="签署日期" width="120" />
      <el-table-column prop="satisfactionScore" label="满意度" width="90">
        <template #default="{ row }">
          <el-rate v-model="row.satisfactionScore" disabled size="small" />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_COMPLETION_CERT_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="520" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:acc-completion-certificate:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'submitCompletionCertificate', '提交')"
            v-hasPermi="['pms:acc-completion-certificate:update']"
            >提交</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 1"
            @click="handleAction(row, 'customerConfirmCompletionCertificate', '客户确认')"
            v-hasPermi="['pms:acc-completion-certificate:update']"
            >客户确认</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 1"
            @click="handleAction(row, 'rejectCompletionCertificate', '驳回')"
            v-hasPermi="['pms:acc-completion-certificate:update']"
            >驳回</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 2"
            @click="handleAction(row, 'archiveCompletionCertificate', '归档')"
            v-hasPermi="['pms:acc-completion-certificate:update']"
            >归档</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:acc-completion-certificate:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑完工证明' : '新增完工证明'" width="780px">
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
          <el-form-item label="证明编号" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="证明名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="客户" prop="customerId">
            <PmsEntitySelect
              v-model="form.customerId"
              :api="CustomerApi.getCustomerPage"
              :label-field="['code', 'name']"
              value-field="id"
              query-field="name"
              placeholder="请选择客户"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="证书编号" prop="certificateNo"><el-input v-model="form.certificateNo" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="签署日期" prop="signedDate">
            <el-date-picker v-model="form.signedDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="满意度评分" prop="satisfactionScore">
            <el-rate v-model="form.satisfactionScore" :max="5" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="客户意见" prop="customerOpinion">
            <Editor v-model="form.customerOpinion" :height="300" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="签章附件" prop="signatureUrl">
            <UploadImg v-model="form.signatureUrl" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="证明附件" prop="attachmentUrl">
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
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import * as CompletionCertificateApi from '@/api/pms/project/completion-certificate'
import * as ProjectApi from '@/api/pms/project/project'
import * as CustomerApi from '@/api/pms/project/customer'
import type { CompletionCertificateVO } from '@/api/pms/project/completion-certificate'

defineOptions({ name: 'PmsCompletionCertificate' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<CompletionCertificateVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  code: '',
  name: '',
  status: undefined
})
const formVisible = ref(false)
const formRef = ref()
const form = reactive<CompletionCertificateVO>({ projectId: undefined!, code: '', name: '' })
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入证明编号' }],
  name: [{ required: true, message: '请输入证明名称' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await CompletionCertificateApi.getCompletionCertificatePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: CompletionCertificateVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: undefined,
      code: '',
      name: '',
      customerId: undefined,
      certificateNo: '',
      signedDate: '',
      satisfactionScore: 5,
      customerOpinion: '',
      signatureUrl: '',
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
    form.id
      ? await CompletionCertificateApi.updateCompletionCertificate(form)
      : await CompletionCertificateApi.createCompletionCertificate(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: CompletionCertificateVO) => {
  await message.delConfirm()
  await CompletionCertificateApi.deleteCompletionCertificate(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (
  row: CompletionCertificateVO,
  action:
    | 'submitCompletionCertificate'
    | 'customerConfirmCompletionCertificate'
    | 'rejectCompletionCertificate'
    | 'archiveCompletionCertificate',
  actionText: string
) => {
  await message.confirm(`确认${actionText}完工证明【${row.code}】？`)
  await (CompletionCertificateApi as any)[action](row.id!)
  message.success(`${actionText}成功`)
  await load()
}

onMounted(load)
</script>
