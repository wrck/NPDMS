<template>
  <ContentWrap>
    <el-alert
      title="站点不绑定公司或办事处；同一地址可创建多个站点。"
      type="info"
      show-icon
      :closable="false"
      class="mb-16px"
    />
    <el-form ref="queryFormRef" :model="queryParams" inline class="-mb-15px" label-width="76px">
      <el-form-item label="站点编码" prop="code"
        ><el-input v-model="queryParams.code" clearable class="!w-200px"
      /></el-form-item>
      <el-form-item label="站点名称" prop="name"
        ><el-input v-model="queryParams.name" clearable class="!w-220px" @keyup.enter="handleQuery"
      /></el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />查询</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button
          type="primary"
          plain
          v-hasPermi="['pms:asset-location:update']"
          @click="openForm()"
          ><Icon icon="ep:plus" class="mr-5px" />新增站点</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column prop="code" label="站点编码" min-width="150" />
      <el-table-column prop="name" label="站点名称" min-width="200" />
      <el-table-column label="地址" min-width="300" show-overflow-tooltip
        ><template #default="scope">{{
          addressName(scope.row.addressId)
        }}</template></el-table-column
      >
      <el-table-column label="客户" min-width="180"
        ><template #default="scope">{{
          customerName(scope.row.customerId)
        }}</template></el-table-column
      >
      <el-table-column prop="siteType" label="站点类型" width="130" />
      <el-table-column prop="version" label="版本" width="80" />
      <el-table-column label="操作" width="180">
        <template #default="scope">
          <el-button
            link
            type="primary"
            v-hasPermi="['pms:asset-location:update']"
            @click="openForm(scope.row.id)"
            >修订</el-button
          >
          <el-button link type="primary" @click="openTree(scope.row)">位置树</el-button>
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

  <Dialog v-model="dialogVisible" :title="formData.id ? '修订站点' : '新增站点'" width="620px">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="92px"
      v-loading="formLoading"
    >
      <el-form-item label="站点编码" prop="code"
        ><el-input v-model="formData.code" placeholder="例如：SITE-HZ-01"
      /></el-form-item>
      <el-form-item label="站点名称" prop="name"><el-input v-model="formData.name" /></el-form-item>
      <el-form-item label="地址" prop="addressId">
        <el-select
          v-model="formData.addressId"
          filterable
          class="!w-100%"
          placeholder="选择可复用地址"
        >
          <el-option
            v-for="item in addressOptions"
            :key="item.id"
            :label="item.fullAddress"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="关联客户" prop="customerId">
        <el-select
          v-model="formData.customerId"
          filterable
          clearable
          class="!w-100%"
          placeholder="可选"
        >
          <el-option
            v-for="item in customerOptions"
            :key="item.id"
            :label="`${item.code} · ${item.name}`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="站点类型" prop="siteType"
        ><el-input v-model="formData.siteType" placeholder="例如：CUSTOMER_SITE"
      /></el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submitForm">保存修订</el-button>
      <el-button @click="dialogVisible = false">取消</el-button>
    </template>
  </Dialog>

  <LocationTreeDrawer ref="treeDrawerRef" @changed="getList" />
</template>

<script lang="ts" setup>
import * as LocationApi from '@/api/pms/asset/location'
import * as CustomerApi from '@/api/pms/project/customer'
import LocationTreeDrawer from './LocationTreeDrawer.vue'

defineOptions({ name: 'PmsAssetSite' })
const message = useMessage()
const loading = ref(false)
const list = ref<LocationApi.SiteVO[]>([])
const total = ref(0)
const addresses = ref<LocationApi.AddressVO[]>([])
const customers = ref<CustomerApi.CustomerVO[]>([])
const addressOptions = computed(() =>
  addresses.value.filter(
    (item): item is LocationApi.AddressVO & { id: number } => item.id !== undefined
  )
)
const customerOptions = computed(() =>
  customers.value.filter(
    (item): item is CustomerApi.CustomerVO & { id: number } => item.id !== undefined
  )
)
const queryFormRef = ref()
const queryParams = reactive({ pageNo: 1, pageSize: 10, code: undefined, name: undefined })
const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref()
const treeDrawerRef = ref()
const emptyForm = (): LocationApi.SiteVO => ({ code: '', name: '', siteType: 'CUSTOMER_SITE' })
const formData = ref<LocationApi.SiteVO>(emptyForm())
const formRules = {
  code: [{ required: true, message: '站点编码不能为空', trigger: 'blur' }],
  name: [{ required: true, message: '站点名称不能为空', trigger: 'blur' }],
  addressId: [{ required: true, message: '地址不能为空', trigger: 'change' }],
  siteType: [{ required: true, message: '站点类型不能为空', trigger: 'blur' }]
}

const loadOptions = async () => {
  const [addressPage, customerPage] = await Promise.all([
    LocationApi.getAddressPage({ pageNo: 1, pageSize: 100 }),
    CustomerApi.getCustomerPage({ pageNo: 1, pageSize: 100 } as PageParam)
  ])
  addresses.value = addressPage.list
  customers.value = customerPage.list
}
const getList = async () => {
  loading.value = true
  try {
    const data = await LocationApi.getSitePage(queryParams)
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
const addressName = (id?: number) =>
  addresses.value.find((item) => item.id === id)?.fullAddress || '地址已不可用'
const customerName = (id?: number) =>
  id ? customers.value.find((item) => item.id === id)?.name || `#${id}` : '未关联'
const openForm = async (id?: number) => {
  formData.value = id ? await LocationApi.getSite(id) : emptyForm()
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}
const submitForm = async () => {
  if (!(await formRef.value?.validate())) return
  const address = addresses.value.find((item) => item.id === formData.value.addressId)
  if (!address?.id) return
  formLoading.value = true
  try {
    await LocationApi.maintainLocation({
      address: { id: address.id, expectedVersion: address.version },
      site: {
        id: formData.value.id,
        expectedVersion: formData.value.version,
        code: formData.value.code,
        name: formData.value.name,
        customerId: formData.value.customerId,
        siteType: formData.value.siteType
      }
    })
    message.success('站点修订已保存')
    dialogVisible.value = false
    await getList()
  } finally {
    formLoading.value = false
  }
}
const openTree = (site: LocationApi.SiteVO) => treeDrawerRef.value?.open(site)
onMounted(async () => {
  await loadOptions()
  await getList()
})
</script>
