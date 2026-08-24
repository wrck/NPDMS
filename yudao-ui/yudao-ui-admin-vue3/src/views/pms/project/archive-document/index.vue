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
      <el-form-item label="文档编号" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="文档名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="文档类型" prop="documentType">
        <el-select v-model="query.documentType" clearable class="!w-120px">
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
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_ACCEPTANCE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:acc-archive-document:create']"
          ><Icon icon="ep:plus" />新增归档文档</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="文档编号" min-width="140" />
      <el-table-column prop="name" label="文档名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="documentType" label="文档类型" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_DOCUMENT_TYPE" :value="row.documentType" />
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="90" />
      <el-table-column prop="uploadedBy" label="上传人" width="100" />
      <el-table-column prop="uploadedDate" label="上传日期" width="120" />
      <el-table-column prop="fileChecksum" label="校验值" min-width="140" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_ACCEPTANCE_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="460" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:acc-archive-document:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'submitArchiveDocument', '提交')"
            v-hasPermi="['pms:acc-archive-document:update']"
            >提交</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 1"
            @click="handleAction(row, 'archiveArchiveDocument', '归档')"
            v-hasPermi="['pms:acc-archive-document:update']"
            >归档</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:acc-archive-document:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑归档文档' : '新增归档文档'" width="780px">
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
          <el-form-item label="文档编号" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="文档名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="文档类型" prop="documentType">
            <el-select v-model="form.documentType" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_DOCUMENT_TYPE)"
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
          <el-form-item label="上传人" prop="uploadedBy"><el-input v-model="form.uploadedBy" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="上传日期" prop="uploadedDate">
            <el-date-picker v-model="form.uploadedDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="文件地址" prop="fileUrl">
            <UploadFile v-model="form.fileUrl" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="文件校验值" prop="fileChecksum">
            <el-input v-model="form.fileChecksum" />
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
import * as ArchiveDocumentApi from '@/api/pms/project/archive-document'
import * as ProjectApi from '@/api/pms/project/project'
import type { ArchiveDocumentVO } from '@/api/pms/project/archive-document'

defineOptions({ name: 'PmsArchiveDocument' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<ArchiveDocumentVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  code: '',
  name: '',
  documentType: undefined,
  status: undefined
})
const formVisible = ref(false)
const formRef = ref()
const form = reactive<ArchiveDocumentVO>({ projectId: undefined!, code: '', name: '' })
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入文档编号' }],
  name: [{ required: true, message: '请输入文档名称' }],
  documentType: [{ required: true, message: '请选择文档类型' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await ArchiveDocumentApi.getArchiveDocumentPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: ArchiveDocumentVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: undefined,
      code: '',
      name: '',
      documentType: 'SCHEME',
      version: '',
      fileUrl: '',
      fileChecksum: '',
      uploadedBy: undefined,
      uploadedDate: '',
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
    form.id ? await ArchiveDocumentApi.updateArchiveDocument(form) : await ArchiveDocumentApi.createArchiveDocument(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: ArchiveDocumentVO) => {
  await message.delConfirm()
  await ArchiveDocumentApi.deleteArchiveDocument(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (
  row: ArchiveDocumentVO,
  action: 'submitArchiveDocument' | 'archiveArchiveDocument',
  actionText: string
) => {
  await message.confirm(`确认${actionText}归档文档【${row.code}】？`)
  await (ArchiveDocumentApi as any)[action](row.id!)
  message.success(`${actionText}成功`)
  await load()
}

onMounted(load)
</script>
