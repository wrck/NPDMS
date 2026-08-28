<template>
  <ContentWrap>
    <el-form :model="query" inline class="-mb-15px">
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
        ><el-button @click="load"><Icon icon="ep:search" />查询</el-button
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
      highlight-current-row
      @current-change="selectCustomer"
    >
      <el-table-column prop="code" label="客户编码" /><el-table-column
        prop="name"
        label="客户名称"
      /><el-table-column prop="departmentName" label="办事处" /><el-table-column
        prop="industryName"
        label="子行业"
      /><el-table-column prop="sourceType" label="来源" /><el-table-column
        prop="lifecycleStatus"
        label="状态"
      />
      <el-table-column label="操作" width="260"
        ><template #default="{ row }"
          ><el-button v-if="row.lifecycleStatus !== 'DELETED'" link @click="openDetail(row.id)"
            >详情</el-button
          ><el-button
            v-if="row.lifecycleStatus !== 'DELETED'"
            link
            v-hasPermi="['pms:customer:update']"
            @click="editCustomer(row.id)"
            >编辑</el-button
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
  <ContentWrap v-if="detail">
    <el-tabs>
      <el-tab-pane label="来源与联系方式"><CustomerSourcePanel :customer="detail" /></el-tab-pane>
      <el-tab-pane label="地点"
        ><CustomerLocationPanel :locations="detail.locations"
      /></el-tab-pane>
      <el-tab-pane label="项目摘要"
        ><CustomerRelationSummaryPanel :slice="detail.projects" kind="project"
      /></el-tab-pane>
      <el-tab-pane label="设备摘要"
        ><CustomerRelationSummaryPanel :slice="detail.devices" kind="device"
      /></el-tab-pane>
      <el-tab-pane label="变更历史"><CustomerHistoryPanel :history="detail.history" /></el-tab-pane>
    </el-tabs>
  </ContentWrap>
  <CustomerFormDrawer ref="formDrawer" @success="load" />
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useMessage } from '@/hooks/web/useMessage'
import * as CustomerApi from '@/api/pms/customer'
import type { CustomerDetailRespVO, CustomerPageReqVO, CustomerRespVO } from '@/api/pms/customer'
import CustomerFormDrawer from './components/CustomerFormDrawer.vue'
import CustomerSourcePanel from './components/CustomerSourcePanel.vue'
import CustomerLocationPanel from './components/CustomerLocationPanel.vue'
import CustomerRelationSummaryPanel from './components/CustomerRelationSummaryPanel.vue'
import CustomerHistoryPanel from './components/CustomerHistoryPanel.vue'
import { createCustomerIntentStore, customerIntentOf } from './customerInteraction'
defineOptions({ name: 'PmsCustomerWorkbench' })
const message = useMessage()
const loading = ref(false)
const rows = ref<CustomerRespVO[]>([])
const total = ref(0)
const detail = ref<CustomerDetailRespVO>()
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
const openDetail = async (id: number) => {
  detail.value = await CustomerApi.getCustomer(id)
}
const editCustomer = async (id: number) => formDrawer.value?.open(await CustomerApi.getCustomer(id))
const selectCustomer = (row?: CustomerRespVO) => {
  if (row && row.lifecycleStatus !== 'DELETED') openDetail(row.id)
  else detail.value = undefined
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
  if (detail.value?.id === row.id) await openDetail(row.id)
}
onMounted(load)
</script>
