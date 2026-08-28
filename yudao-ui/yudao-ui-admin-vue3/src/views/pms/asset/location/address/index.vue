<template>
  <ContentWrap>
    <el-alert
      title="地址是可复用主数据，同一地址可承载多个站点。"
      type="info"
      show-icon
      :closable="false"
      class="mb-16px"
    />
    <el-form ref="queryFormRef" :model="queryParams" inline class="-mb-15px" label-width="76px">
      <el-form-item label="完整地址" prop="fullAddress">
        <el-input
          v-model="queryParams.fullAddress"
          clearable
          placeholder="搜索完整地址"
          class="!w-280px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="区县编码" prop="districtCode">
        <el-input
          v-model="queryParams.districtCode"
          clearable
          placeholder="例如：330106"
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />查询</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button
          type="primary"
          plain
          v-hasPermi="['pms:asset-location:update']"
          @click="openForm()"
        >
          <Icon icon="ep:plus" class="mr-5px" />新增地址
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column prop="fullAddress" label="完整地址" min-width="320" show-overflow-tooltip />
      <el-table-column label="行政区划" min-width="240">
        <template #default="scope">{{ divisionText(scope.row) }}</template>
      </el-table-column>
      <el-table-column
        prop="detailAddress"
        label="详细地址"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column label="坐标" width="190">
        <template #default="scope">{{ coordinateText(scope.row) }}</template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="80" />
      <el-table-column label="操作" width="90">
        <template #default="scope"
          ><el-button
            link
            type="primary"
            v-hasPermi="['pms:asset-location:update']"
            @click="openForm(scope.row.id)"
            >修订</el-button
          ></template
        >
      </el-table-column>
    </el-table>
    <Pagination
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <Dialog v-model="dialogVisible" :title="formData.id ? '修订地址' : '新增地址'" width="760px">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="96px"
      v-loading="formLoading"
    >
      <el-divider content-position="left">行政区划</el-divider>
      <el-row :gutter="16">
        <el-col v-for="field in divisionFields" :key="field.code" :xs="24" :sm="12">
          <el-form-item :label="field.label + '编码'" :prop="field.code"
            ><el-input v-model="formData[field.code]"
          /></el-form-item>
          <el-form-item :label="field.label + '名称'" :prop="field.name"
            ><el-input v-model="formData[field.name]"
          /></el-form-item>
        </el-col>
      </el-row>
      <el-divider content-position="left">地址明细</el-divider>
      <el-form-item label="详细地址" prop="detailAddress"
        ><el-input v-model="formData.detailAddress" placeholder="道路、门牌号等"
      /></el-form-item>
      <el-form-item label="完整地址" prop="fullAddress"
        ><el-input v-model="formData.fullAddress" type="textarea" :rows="2"
      /></el-form-item>
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12"
          ><el-form-item label="经度" prop="longitude"
            ><el-input-number
              v-model="formData.longitude"
              :precision="7"
              :controls="false"
              class="!w-100%" /></el-form-item
        ></el-col>
        <el-col :xs="24" :sm="12"
          ><el-form-item label="纬度" prop="latitude"
            ><el-input-number
              v-model="formData.latitude"
              :precision="7"
              :controls="false"
              class="!w-100%" /></el-form-item
        ></el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submitForm">保存修订</el-button>
      <el-button @click="dialogVisible = false">取消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as LocationApi from '@/api/pms/asset/location'

defineOptions({ name: 'PmsAssetAddress' })
const message = useMessage()
const loading = ref(false)
const list = ref<LocationApi.AddressVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  fullAddress: undefined,
  districtCode: undefined
})
const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref()
type DivisionKey =
  | 'countryCode'
  | 'countryName'
  | 'provinceCode'
  | 'provinceName'
  | 'cityCode'
  | 'cityName'
  | 'districtCode'
  | 'districtName'
const divisionFields: { label: string; code: DivisionKey; name: DivisionKey }[] = [
  { label: '国家', code: 'countryCode', name: 'countryName' },
  { label: '省', code: 'provinceCode', name: 'provinceName' },
  { label: '市', code: 'cityCode', name: 'cityName' },
  { label: '区县', code: 'districtCode', name: 'districtName' }
]
const emptyForm = (): LocationApi.AddressVO => ({ detailAddress: '', fullAddress: '' })
const formData = ref<LocationApi.AddressVO>(emptyForm())
const formRules = {
  countryCode: [{ required: true, message: '国家编码不能为空', trigger: 'blur' }],
  countryName: [{ required: true, message: '国家名称不能为空', trigger: 'blur' }],
  detailAddress: [{ required: true, message: '详细地址不能为空', trigger: 'blur' }],
  fullAddress: [{ required: true, message: '完整地址不能为空', trigger: 'blur' }]
}

const getList = async () => {
  loading.value = true
  try {
    const data = await LocationApi.getAddressPage(queryParams)
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
const divisionText = (row: LocationApi.AddressVO) =>
  [row.countryName, row.provinceName, row.cityName, row.districtName].filter(Boolean).join(' / ')
const coordinateText = (row: LocationApi.AddressVO) =>
  row.longitude == null || row.latitude == null ? '未维护' : `${row.longitude}, ${row.latitude}`
const openForm = async (id?: number) => {
  formData.value = id ? await LocationApi.getAddress(id) : emptyForm()
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}
const submitForm = async () => {
  if (!(await formRef.value?.validate())) return
  formLoading.value = true
  try {
    await LocationApi.maintainLocation({
      address: {
        id: formData.value.id,
        expectedVersion: formData.value.version,
        countryCode: formData.value.countryCode,
        countryName: formData.value.countryName,
        provinceCode: formData.value.provinceCode,
        provinceName: formData.value.provinceName,
        cityCode: formData.value.cityCode,
        cityName: formData.value.cityName,
        districtCode: formData.value.districtCode,
        districtName: formData.value.districtName,
        detailAddress: formData.value.detailAddress,
        fullAddress: formData.value.fullAddress,
        longitude: formData.value.longitude,
        latitude: formData.value.latitude
      }
    })
    message.success('地址修订已保存')
    dialogVisible.value = false
    await getList()
  } finally {
    formLoading.value = false
  }
}
onMounted(getList)
</script>
