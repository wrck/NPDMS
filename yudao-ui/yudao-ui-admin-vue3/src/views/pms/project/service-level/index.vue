<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="客户" prop="customerId">
        <PmsEntitySelect
          v-model="query.customerId"
          :api="CustomerApi.getCustomerPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择客户"
          class="!w-200px"
        />
      </el-form-item>
      <el-form-item label="服务等级" prop="level">
        <el-select v-model="query.level" clearable class="!w-160px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_SERVICE_LEVEL)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_SRV_LEVEL_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:service-level:create']"
          ><Icon icon="ep:plus" />新增服务等级</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="customerName" label="客户名称" min-width="160" />
      <el-table-column prop="level" label="服务等级" width="110">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_SERVICE_LEVEL" :value="row.level" />
        </template>
      </el-table-column>
      <el-table-column prop="validFrom" label="生效开始" width="120" />
      <el-table-column prop="validTo" label="生效结束" width="120" />
      <el-table-column prop="responseTimeHours" label="响应时间(h)" width="110" />
      <el-table-column prop="proactiveService" label="主动服务" width="100">
        <template #default="{ row }">
          <el-tag :type="row.proactiveService ? 'success' : 'info'">{{ row.proactiveService ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_SRV_LEVEL_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:service-level:update']"
            >编辑</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:service-level:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑服务等级' : '新增服务等级'" width="620px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="客户" prop="customerId">
        <PmsEntitySelect
          v-model="form.customerId"
          :api="CustomerApi.getCustomerPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择客户"
          :disabled="!!form.id"
        />
      </el-form-item>
      <el-form-item label="服务等级" prop="level">
        <el-select v-model="form.level" class="!w-full">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_SERVICE_LEVEL)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="生效开始" prop="validFrom">
        <el-date-picker v-model="form.validFrom" type="date" value-format="YYYY-MM-DD" class="!w-full" />
      </el-form-item>
      <el-form-item label="生效结束" prop="validTo">
        <el-date-picker v-model="form.validTo" type="date" value-format="YYYY-MM-DD" class="!w-full" />
      </el-form-item>
      <el-form-item label="响应时间(h)" prop="responseTimeHours">
        <el-input-number v-model="form.responseTimeHours" :min="0" class="!w-full" />
      </el-form-item>
      <el-form-item label="主动服务">
        <el-switch v-model="form.proactiveService" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import * as ServiceLevelApi from '@/api/pms/project/service-level'
import * as CustomerApi from '@/api/pms/project/customer'
import type { CustomerServiceLevelVO } from '@/api/pms/project/service-level'

defineOptions({ name: 'PmsCustomerServiceLevel' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<CustomerServiceLevelVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, customerId: undefined, level: '', status: undefined })
const formVisible = ref(false)
const formRef = ref()
const form = reactive<CustomerServiceLevelVO>({ customerId: 0, level: 'STANDARD', status: 0, proactiveService: false })
const rules = {
  customerId: [{ required: true, message: '请选择客户' }],
  level: [{ required: true, message: '请选择服务等级' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await ServiceLevelApi.getServiceLevelPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: CustomerServiceLevelVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      customerId: 0,
      level: 'STANDARD',
      validFrom: '',
      validTo: '',
      status: 0,
      responseTimeHours: undefined,
      proactiveService: false,
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
    form.id ? await ServiceLevelApi.updateServiceLevel(form) : await ServiceLevelApi.createServiceLevel(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: CustomerServiceLevelVO) => {
  await message.delConfirm()
  await ServiceLevelApi.deleteServiceLevel(row.id!)
  message.success('删除成功')
  await load()
}
onMounted(load)
</script>
