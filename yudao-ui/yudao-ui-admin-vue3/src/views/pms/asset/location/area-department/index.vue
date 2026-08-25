<template>
  <ContentWrap>
    <el-alert
      title="仅按行政区划编码+层级精确提示服务办事处，不向父级回退，最终指派由授权人员确认。"
      type="warning"
      show-icon
      :closable="false"
      class="mb-16px"
    />
    <el-form ref="queryFormRef" :model="queryParams" inline class="-mb-15px" label-width="86px">
      <el-form-item label="区划编码" prop="areaCode"
        ><el-input v-model="queryParams.areaCode" clearable class="!w-180px"
      /></el-form-item>
      <el-form-item label="区划层级" prop="areaLevel">
        <el-select v-model="queryParams.areaLevel" clearable class="!w-160px"
          ><el-option
            v-for="item in areaLevels"
            :key="item.value"
            :label="item.label"
            :value="item.value"
        /></el-select>
      </el-form-item>
      <el-form-item label="部门编码" prop="departmentCode"
        ><el-input v-model="queryParams.departmentCode" clearable class="!w-190px"
      /></el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />查询</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button
          type="primary"
          plain
          v-hasPermi="['pms:asset-location:update']"
          @click="openForm()"
          ><Icon icon="ep:plus" class="mr-5px" />新增映射</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column prop="areaCode" label="区划编码" min-width="140" />
      <el-table-column label="区划层级" width="120"
        ><template #default="scope">{{
          areaLevelLabel(scope.row.areaLevel)
        }}</template></el-table-column
      >
      <el-table-column label="服务办事处" min-width="240"
        ><template #default="scope"
          ><span class="font-500">{{ scope.row.departmentName || '部门已不可用' }}</span
          ><span class="ml-8px text-12px text-[var(--el-text-color-secondary)]">{{
            scope.row.departmentCode
          }}</span></template
        ></el-table-column
      >
      <el-table-column label="生效区间" min-width="300"
        ><template #default="scope"
          >{{ formatDate(scope.row.effectiveFrom) }} →
          {{ scope.row.effectiveTo ? formatDate(scope.row.effectiveTo) : '长期' }}</template
        ></el-table-column
      >
      <el-table-column label="状态" width="100"
        ><template #default="scope"
          ><dict-tag :type="DICT_TYPE.COMMON_STATUS" :value="scope.row.status" /></template
      ></el-table-column>
      <el-table-column prop="version" label="版本" width="80" />
      <el-table-column label="操作" width="90"
        ><template #default="scope"
          ><el-button
            link
            type="primary"
            v-hasPermi="['pms:asset-location:update']"
            @click="openForm(scope.row)"
            >修订</el-button
          ></template
        ></el-table-column
      >
    </el-table>
    <Pagination
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <Dialog
    v-model="dialogVisible"
    :title="formData.id ? '修订区划映射' : '新增区划映射'"
    width="600px"
  >
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="106px">
      <el-row :gutter="16">
        <el-col :span="12"
          ><el-form-item label="区划编码" prop="areaCode"
            ><el-input v-model="formData.areaCode" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="区划层级" prop="areaLevel"
            ><el-select v-model="formData.areaLevel" class="!w-100%"
              ><el-option
                v-for="item in areaLevels"
                :key="item.value"
                :label="item.label"
                :value="item.value" /></el-select></el-form-item
        ></el-col>
      </el-row>
      <el-form-item label="服务办事处" prop="departmentCode">
        <el-select
          v-model="formData.departmentCode"
          filterable
          class="!w-100%"
          placeholder="选择组织架构中的部门"
        >
          <el-option
            v-for="dept in departments"
            :key="dept.code"
            :label="`${dept.name} · ${dept.code}`"
            :value="dept.code"
          />
        </el-select>
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12"
          ><el-form-item label="生效时间" prop="effectiveFrom"
            ><el-date-picker
              v-model="formData.effectiveFrom"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              class="!w-100%" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="失效时间" prop="effectiveTo"
            ><el-date-picker
              v-model="formData.effectiveTo"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              clearable
              class="!w-100%" /></el-form-item
        ></el-col>
      </el-row>
      <el-form-item label="状态" prop="status"
        ><el-radio-group v-model="formData.status"
          ><el-radio
            v-for="item in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
            :key="item.value"
            :value="item.value"
            >{{ item.label }}</el-radio
          ></el-radio-group
        ></el-form-item
      >
    </el-form>
    <template #footer
      ><el-button type="primary" @click="submitForm">保存修订</el-button
      ><el-button @click="dialogVisible = false">取消</el-button></template
    >
  </Dialog>
</template>

<script lang="ts" setup>
import dayjs from 'dayjs'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { CommonStatusEnum } from '@/utils/constants'
import * as DeptApi from '@/api/system/dept'
import * as LocationApi from '@/api/pms/asset/location'

defineOptions({ name: 'PmsAssetAreaDepartment' })
const message = useMessage()
const areaLevels = [
  { label: '国家', value: 'COUNTRY' },
  { label: '省', value: 'PROVINCE' },
  { label: '市', value: 'CITY' },
  { label: '区县', value: 'DISTRICT' }
]
const loading = ref(false)
const list = ref<LocationApi.AreaDepartmentMappingVO[]>([])
const total = ref(0)
const departments = ref<DeptApi.DeptVO[]>([])
const queryFormRef = ref()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  areaCode: undefined,
  areaLevel: undefined,
  departmentCode: undefined
})
const dialogVisible = ref(false)
const formRef = ref()
const emptyForm = (): LocationApi.AreaDepartmentMappingVO => ({
  areaCode: '',
  areaLevel: 'DISTRICT',
  departmentCode: '',
  effectiveFrom: dayjs().format('YYYY-MM-DDTHH:mm:ss'),
  status: CommonStatusEnum.ENABLE
})
const formData = ref<LocationApi.AreaDepartmentMappingVO>(emptyForm())
const formRules = {
  areaCode: [{ required: true, message: '区划编码不能为空', trigger: 'blur' }],
  areaLevel: [{ required: true, message: '区划层级不能为空', trigger: 'change' }],
  departmentCode: [{ required: true, message: '服务办事处不能为空', trigger: 'change' }],
  effectiveFrom: [{ required: true, message: '生效时间不能为空', trigger: 'change' }]
}
const getList = async () => {
  loading.value = true
  try {
    const data = await LocationApi.getAreaDepartmentMappingPage(queryParams)
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
const areaLevelLabel = (value: string) =>
  areaLevels.find((item) => item.value === value)?.label || value
const formatDate = (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm')
const openForm = (row?: LocationApi.AreaDepartmentMappingVO) => {
  formData.value = row ? { ...row, expectedVersion: row.version } : emptyForm()
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}
const submitForm = async () => {
  if (!(await formRef.value?.validate())) return
  await LocationApi.saveAreaDepartmentMapping({
    id: formData.value.id,
    expectedVersion: formData.value.version,
    areaCode: formData.value.areaCode,
    areaLevel: formData.value.areaLevel,
    departmentCode: formData.value.departmentCode,
    effectiveFrom: formData.value.effectiveFrom,
    effectiveTo: formData.value.effectiveTo,
    status: formData.value.status
  })
  message.success('区划与服务办事处映射已保存')
  dialogVisible.value = false
  await getList()
}
onMounted(async () => {
  departments.value = (await DeptApi.getSimpleDeptList()).filter((item) => item.code)
  await getList()
})
</script>
