<template>
  <el-drawer v-model="visible" size="min(820px, 100%)" title="分配版本与明细历史">
    <el-table v-loading="loading" :data="rows" empty-text="没有可见历史版本">
      <el-table-column prop="allocationVersion" label="分配版本" min-width="100" />
      <el-table-column prop="scopeStatus" label="状态" min-width="110" />
      <el-table-column prop="allocatedQuantity" label="数量" min-width="100" />
      <el-table-column prop="officeDepartmentName" label="发生时办事处" min-width="160" />
      <el-table-column prop="effectiveFrom" label="生效时间" min-width="170" />
      <el-table-column prop="effectiveTo" label="失效时间" min-width="170">
        <template #default="{ row }">{{ row.effectiveTo || '当前有效' }}</template>
      </el-table-column>
      <el-table-column type="expand">
        <template #default="{ row }">
          <el-table :data="row.details" size="small" empty-text="无明细">
            <el-table-column prop="sequence" label="#" width="60" />
            <el-table-column prop="serialNo" label="序列号" min-width="140" />
            <el-table-column prop="productCode" label="产品编码" min-width="130" />
            <el-table-column prop="deviceTypeCode" label="设备类型" min-width="130" />
            <el-table-column prop="allocatedQuantity" label="数量" min-width="90" />
          </el-table>
        </template>
      </el-table-column>
    </el-table>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import * as CommerceApi from '@/api/pms/commerce'
import type { DeliveryScopeRespVO } from '@/api/pms/commerce'

defineOptions({ name: 'PmsCommerceDeliveryScopeHistory' })
const visible = ref(false)
const loading = ref(false)
const rows = ref<DeliveryScopeRespVO[]>([])

const open = async (scope: DeliveryScopeRespVO) => {
  visible.value = true
  loading.value = true
  try {
    const data = await CommerceApi.getDeliveryScopePage({
      pageNo: 1,
      pageSize: 200,
      projectId: scope.projectId,
      orderLineId: scope.orderLineId,
      includeHistory: true
    })
    rows.value = data.list
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>
