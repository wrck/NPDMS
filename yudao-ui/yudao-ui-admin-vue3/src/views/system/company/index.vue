<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="queryParams" inline class="-mb-15px" label-width="76px">
      <el-form-item label="公司编码" prop="code">
        <el-input
          v-model="queryParams.code"
          clearable
          placeholder="请输入公司编码"
          class="!w-220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="公司名称" prop="name">
        <el-input
          v-model="queryParams.name"
          clearable
          placeholder="请输入公司名称"
          class="!w-220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" clearable placeholder="全部" class="!w-160px">
          <el-option
            v-for="item in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />查询</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button type="primary" plain v-hasPermi="['system:company:create']" @click="openForm()">
          <Icon icon="ep:plus" class="mr-5px" />新增公司
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column prop="code" label="公司编码" min-width="160" />
      <el-table-column prop="name" label="公司名称" min-width="220" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="scope"
          ><dict-tag :type="DICT_TYPE.COMMON_STATUS" :value="scope.row.status"
        /></template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="90" />
      <el-table-column prop="createTime" label="创建时间" width="180" :formatter="dateFormatter" />
      <el-table-column label="操作" width="100">
        <template #default="scope">
          <el-button
            link
            type="primary"
            v-hasPermi="['system:company:update']"
            @click="openForm(scope.row.id)"
            >修订</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <Dialog v-model="dialogVisible" :title="formData.id ? '修订公司' : '新增公司'">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="90px"
    >
      <el-form-item label="公司编码" prop="code"
        ><el-input v-model="formData.code" placeholder="例如：DPTECH"
      /></el-form-item>
      <el-form-item label="公司名称" prop="name"><el-input v-model="formData.name" /></el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio
            v-for="item in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
            :key="item.value"
            :value="item.value"
            >{{ item.label }}</el-radio
          >
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="formData.version !== undefined" label="当前版本"
        ><el-tag type="info">v{{ formData.version }}</el-tag></el-form-item
      >
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submitForm">保存</el-button>
      <el-button @click="dialogVisible = false">取消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import { CommonStatusEnum } from '@/utils/constants'
import * as CompanyApi from '@/api/system/company'

defineOptions({ name: 'SystemCompany' })
const message = useMessage()
const loading = ref(false)
const list = ref<CompanyApi.CompanyVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  code: undefined,
  name: undefined,
  status: undefined
})
const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref()
const emptyForm = (): CompanyApi.CompanyVO => ({
  code: '',
  name: '',
  status: CommonStatusEnum.ENABLE
})
const formData = ref<CompanyApi.CompanyVO>(emptyForm())
const formRules = {
  code: [{ required: true, message: '公司编码不能为空', trigger: 'blur' }],
  name: [{ required: true, message: '公司名称不能为空', trigger: 'blur' }],
  status: [{ required: true, message: '状态不能为空', trigger: 'change' }]
}

const getList = async () => {
  loading.value = true
  try {
    const data = await CompanyApi.getCompanyPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}
const openForm = async (id?: number) => {
  formData.value = id ? await CompanyApi.getCompany(id) : emptyForm()
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}
const submitForm = async () => {
  if (!(await formRef.value?.validate())) return
  formLoading.value = true
  try {
    if (formData.value.id) {
      await CompanyApi.updateCompany({
        id: formData.value.id,
        code: formData.value.code,
        name: formData.value.name,
        status: formData.value.status,
        expectedVersion: formData.value.version
      })
      message.success('公司修订已保存')
    } else {
      await CompanyApi.createCompany(formData.value)
      message.success('公司已创建')
    }
    dialogVisible.value = false
    await getList()
  } finally {
    formLoading.value = false
  }
}
onMounted(getList)
</script>
