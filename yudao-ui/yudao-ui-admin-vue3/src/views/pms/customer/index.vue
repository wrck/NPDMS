<template>
  <ContentWrap>
    <el-form
      :model="query"
      inline
      class="query-form"
      @submit.prevent="search"
      @keyup.enter="search"
    >
      <el-form-item label="客户编码"><el-input v-model="query.code" clearable /></el-form-item>
      <el-form-item label="客户名称"><el-input v-model="query.name" clearable /></el-form-item>
      <el-form-item label="办事处"
        ><el-input v-model="query.departmentCode" clearable
      /></el-form-item>
      <el-form-item label="市场部"><el-input v-model="query.marketCode" clearable /></el-form-item>
      <el-form-item label="系统部"><el-input v-model="query.systemCode" clearable /></el-form-item>
      <el-form-item label="拓展部"><el-input v-model="query.expendCode" clearable /></el-form-item>
      <el-form-item label="子行业"
        ><el-input v-model="query.industryCode" clearable
      /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.lifecycleStatus" clearable style="width: 140px">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
          <el-option label="已删除" value="DELETED" />
        </el-select>
      </el-form-item>
      <el-form-item
        ><el-button @click="search"><Icon icon="ep:search" />查询</el-button
        ><el-button @click="reset">重置</el-button
        ><el-button type="primary" v-hasPermi="['pms:customer:create']" @click="formDrawer?.open()"
          ><Icon icon="ep:plus" />创建客户</el-button
        ></el-form-item
      >
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table
      v-loading="loading"
      :data="rows"
      row-key="id"
      empty-text="没有匹配的客户，请调整筛选条件"
      highlight-current-row
    >
      <el-table-column prop="code" label="客户编码" min-width="180" show-overflow-tooltip />
      <el-table-column prop="name" label="客户名称" min-width="220" show-overflow-tooltip />
      <el-table-column prop="departmentName" label="办事处" min-width="140" show-overflow-tooltip />
      <el-table-column prop="industryName" label="子行业" min-width="140" show-overflow-tooltip />
      <el-table-column label="来源" width="120">
        <template #default="{ row }">{{ sourceLabels[row.sourceType] || row.sourceType }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.lifecycleStatus === 'ENABLED' ? 'success' : 'info'">
            {{ statusLabels[row.lifecycleStatus] || row.lifecycleStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right"
        ><template #default="{ row }"
          ><el-button v-if="row.lifecycleStatus !== 'DELETED'" link @click="openCustomer(row.id)"
            >详情</el-button
          ><el-button
            v-if="row.lifecycleStatus === 'ENABLED'"
            link
            v-hasPermi="['pms:customer:disable']"
            @click="runLifecycle(row, 'disable')"
            >停用</el-button
          ><el-button
            v-if="row.lifecycleStatus !== 'DELETED'"
            link
            type="danger"
            v-hasPermi="['pms:customer:delete']"
            @click="runLifecycle(row, 'delete')"
            >删除</el-button
          ><el-button
            v-else
            link
            v-hasPermi="['pms:customer:restore']"
            @click="runLifecycle(row, 'restore')"
            >恢复</el-button
          ></template
        ></el-table-column
      >
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>
  <CustomerFormDrawer ref="formDrawer" @success="load" />
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useMessage } from '@/hooks/web/useMessage'
import * as CustomerApi from '@/api/pms/customer'
import type { CustomerPageReqVO, CustomerRespVO } from '@/api/pms/customer'
import CustomerFormDrawer from './components/CustomerFormDrawer.vue'
import { createCustomerIntentStore, customerIntentOf } from './customerInteraction'
defineOptions({ name: 'PmsCustomerWorkbench' })
const message = useMessage()
const loading = ref(false)
const rows = ref<CustomerRespVO[]>([])
const total = ref(0)
let formRequest = 0
const sourceLabels: Record<string, string> = {
  CRM_SYNC: 'CRM 同步',
  PLATFORM_CREATED: '平台创建',
  PLATFORM_TEMPORARY: '平台临时'
}
const statusLabels: Record<string, string> = {
  ENABLED: '启用',
  DISABLED: '停用',
  DELETED: '已删除'
}
const formDrawer = ref<InstanceType<typeof CustomerFormDrawer>>()
const intentKeys = createCustomerIntentStore()
const query = reactive<CustomerPageReqVO>({ pageNo: 1, pageSize: 10 })
const load = async () => {
  loading.value = true
  try {
    const data = await CustomerApi.getCustomerPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const search = () => {
  query.pageNo = 1
  return load()
}
const reset = () => {
  Object.assign(query, {
    code: undefined,
    name: undefined,
    departmentCode: undefined,
    marketCode: undefined,
    systemCode: undefined,
    expendCode: undefined,
    industryCode: undefined,
    lifecycleStatus: undefined
  })
  return search()
}
const openCustomer = async (id: number) => {
  const request = ++formRequest
  const customer = await CustomerApi.getCustomer(id)
  if (request === formRequest) formDrawer.value?.open(customer)
}
const runLifecycle = async (row: CustomerRespVO, action: 'disable' | 'delete' | 'restore') => {
  const { value } = await ElMessageBox.prompt('请输入操作原因', '客户生命周期操作', {
    inputPattern: /\S+/,
    inputErrorMessage: '原因不能为空'
  })
  const data = { reason: value }
  const intent = customerIntentOf(action, { id: row.id, version: row.version, data })
  const idempotencyKey = intentKeys.key(intent)
  if (action === 'disable')
    await CustomerApi.disableCustomer(row.id, data, row.version, idempotencyKey)
  if (action === 'delete')
    await CustomerApi.deleteCustomer(row.id, data, row.version, idempotencyKey)
  if (action === 'restore')
    await CustomerApi.restoreCustomer(row.id, data, row.version, idempotencyKey)
  intentKeys.complete(intent)
  message.success('操作成功')
  await load()
}
onMounted(load)
</script>
<style scoped>
.query-form :deep(.el-input) {
  width: 180px;
}

@media (width <= 767px) {
  .query-form {
    display: grid;
  }

  .query-form :deep(.el-form-item),
  .query-form :deep(.el-input) {
    width: 100%;
    margin-right: 0;
  }
}
</style>
