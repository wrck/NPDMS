<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="编号" prop="code">
        <el-input v-model="query.code" clearable class="!w-180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-180px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="产品类型" prop="productType">
        <el-select v-model="query.productType" clearable class="!w-160px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_PRODUCT_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_FORM_TEMPLATE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openCreate()" v-hasPermi="['pms:eng-form-template:create']"
          ><Icon icon="ep:plus" />新建模板</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无表单模板数据">
      <el-table-column prop="code" label="编号" width="160" />
      <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="productType" label="产品类型" width="140">
        <template #default="{ row }">
          <dict-tag v-if="row.productType" :type="DICT_TYPE.PMS_PRODUCT_TYPE" :value="row.productType" />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_FORM_TEMPLATE_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="80" />
      <el-table-column prop="createTime" label="创建时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)" v-hasPermi="['pms:eng-form-template:query']"
            >明细</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0"
            @click="openEdit(row)"
            v-hasPermi="['pms:eng-form-template:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handlePublish(row)"
            v-hasPermi="['pms:eng-form-template:publish']"
            >发布</el-button
          >
          <el-button
            link
            type="info"
            v-if="row.status === 1"
            @click="handleDisable(row)"
            v-hasPermi="['pms:eng-form-template:publish']"
            >停用</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 2"
            @click="handleEnable(row)"
            v-hasPermi="['pms:eng-form-template:publish']"
            >启用</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 0"
            @click="remove(row)"
            v-hasPermi="['pms:eng-form-template:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <!-- 新建/编辑对话框 -->
  <Dialog v-model="formVisible" :title="form.id ? '编辑表单模板' : '新建表单模板'" width="960px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="编号" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 FT-2026-001" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="产品类型" prop="productType">
            <el-select v-model="form.productType" clearable class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_PRODUCT_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col v-if="form.id" :span="12">
          <el-form-item label="版本号" prop="version">
            <el-input-number v-model="form.version" :min="0" class="!w-full" disabled />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="模板说明" prop="description">
            <el-input v-model="form.description" type="textarea" :rows="2" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="表单配置(conf)" prop="conf">
            <el-input v-model="form.conf" type="textarea" :rows="4" placeholder='如 {"form":{"labelPosition":"top","size":"default"}}' />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="表单字段(fields)" prop="fields">
            <el-input v-model="form.fields" type="textarea" :rows="8" placeholder='如 [{"type":"input","field":"deviceModel","title":"设备型号"}]' />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>

  <!-- 明细对话框 -->
  <Dialog v-model="detailVisible" title="表单模板明细" width="960px">
    <el-descriptions :column="2" border class="mb-15px">
      <el-descriptions-item label="编号">{{ current.code }}</el-descriptions-item>
      <el-descriptions-item label="名称">{{ current.name }}</el-descriptions-item>
      <el-descriptions-item label="产品类型">
        <dict-tag :type="DICT_TYPE.PMS_PRODUCT_TYPE" :value="current.productType" />
      </el-descriptions-item>
      <el-descriptions-item label="状态">
        <dict-tag :type="DICT_TYPE.PMS_FORM_TEMPLATE_STATUS" :value="current.status" />
      </el-descriptions-item>
      <el-descriptions-item label="版本号">{{ current.version }}</el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ current.createTime }}</el-descriptions-item>
      <el-descriptions-item label="说明" :span="2">{{ current.description || '-' }}</el-descriptions-item>
      <el-descriptions-item label="表单配置(conf)" :span="2">
        <pre class="whitespace-pre-wrap break-all">{{ current.conf }}</pre>
      </el-descriptions-item>
      <el-descriptions-item label="表单字段(fields)" :span="2">
        <pre class="whitespace-pre-wrap break-all">{{ current.fields }}</pre>
      </el-descriptions-item>
    </el-descriptions>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as FormTemplateApi from '@/api/pms/engineering/form-template'
import type { FormTemplateVO } from '@/api/pms/engineering/form-template'

defineOptions({ name: 'PmsEngFormTemplate' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<FormTemplateVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  code: '',
  name: '',
  productType: '',
  status: undefined as number | undefined
})

const load = async () => {
  loading.value = true
  try {
    const data = await FormTemplateApi.getFormTemplatePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 新建/编辑
const formVisible = ref(false)
const formRef = ref()
const form = reactive<FormTemplateVO>({
  code: '',
  name: '',
  productType: '',
  conf: '{"form":{"labelPosition":"top","size":"default"}}',
  fields: '[]',
  description: '',
  version: 0
})
const rules = {
  code: [{ required: true, message: '请输入编号' }],
  name: [{ required: true, message: '请输入名称' }],
  conf: [{ required: true, message: '请输入表单配置' }],
  fields: [{ required: true, message: '请输入表单字段' }]
}

const openCreate = () => {
  Object.assign(form, {
    id: undefined,
    code: '',
    name: '',
    productType: '',
    conf: '{"form":{"labelPosition":"top","size":"default"}}',
    fields: '[]',
    description: '',
    version: 0
  })
  formVisible.value = true
}
const openEdit = async (row: FormTemplateVO) => {
  const detail = await FormTemplateApi.getFormTemplate(row.id!)
  Object.assign(form, detail)
  formVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await FormTemplateApi.updateFormTemplate(form)
      message.success('更新成功')
    } else {
      await FormTemplateApi.createFormTemplate(form)
      message.success('创建成功')
    }
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// 明细
const detailVisible = ref(false)
const current = ref<Partial<FormTemplateVO>>({})
const openDetail = async (row: FormTemplateVO) => {
  current.value = await FormTemplateApi.getFormTemplate(row.id!)
  detailVisible.value = true
}

// 状态操作
const handlePublish = async (row: FormTemplateVO) => {
  await message.confirm('确认发布此表单模板？发布后不可修改。')
  await FormTemplateApi.publishFormTemplate(row.id!)
  message.success('发布成功')
  await load()
}
const handleDisable = async (row: FormTemplateVO) => {
  await message.confirm('确认停用此表单模板？停用后不可创建新实例。')
  await FormTemplateApi.disableFormTemplate(row.id!)
  message.success('停用成功')
  await load()
}
const handleEnable = async (row: FormTemplateVO) => {
  await message.confirm('确认重新启用此表单模板？')
  await FormTemplateApi.enableFormTemplate(row.id!)
  message.success('启用成功')
  await load()
}
const remove = async (row: FormTemplateVO) => {
  await message.delConfirm()
  await FormTemplateApi.deleteFormTemplate(row.id!)
  message.success('删除成功')
  await load()
}

onMounted(load)
</script>
