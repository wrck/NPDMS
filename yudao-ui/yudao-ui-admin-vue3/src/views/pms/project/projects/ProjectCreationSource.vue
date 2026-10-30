<template>
  <div v-loading="loading" class="source-preview">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-button v-if="error" link type="primary" @click="$emit('select-order', salesOrderId)">重新加载来源</el-button>
    <template v-if="source">
      <el-form-item label="关联销售订单">
        <el-select :model-value="salesOrderId" placeholder="选择此合同下的销售订单" class="!w-full"
          :disabled="loading" @change="$emit('select-order', $event)">
          <el-option v-for="order in source.orders" :key="order.id" :value="order.id"
            :label="`${order.orderNo} · 执行单 ${order.executionNo || '未关联'}`" />
        </el-select>
      </el-form-item>
      <el-alert v-if="!source.orders.length" title="此合同没有有效销售订单，请核对同步数据。" type="warning" :closable="false" />
      <el-descriptions v-if="salesOrderId" title="来源基本信息" :column="mobile ? 1 : 4" border size="small">
        <el-descriptions-item label="订单项目名称">{{ source.resolved.projectName ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="关联执行单">{{ source.resolved.executionNo ?? '未关联' }}</el-descriptions-item>
        <el-descriptions-item label="订单购货方">{{ source.resolved.customerName ?? '—' }}（{{ source.resolved.customerCode ?? '—' }}）</el-descriptions-item>
        <el-descriptions-item label="客户项目名称">{{ source.resolved.customerProjectName ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="重大项目级别">{{ dictText(source.resolved.majorProjectLevel, DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL) }}</el-descriptions-item>
        <el-descriptions-item label="项目类型">{{ dictText(source.resolved.projectType, DICT_TYPE.PMS_PROJECT_TYPE) }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ source.resolved.orderCreateTime ? formatDate(source.resolved.orderCreateTime) : '—' }}</el-descriptions-item>
        <el-descriptions-item label="销售代表">{{ execution?.salesRepName ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="市场 / 系统">{{ source.resolved.marketName ?? '—' }} / {{ source.resolved.systemName ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="拓展 / 行业">{{ source.resolved.expendName ?? '—' }} / {{ source.resolved.industryName ?? '—' }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="source.orders.length" class="source-block">
        <h4>关联销售订单</h4>
        <el-table :data="source.orders" size="small" max-height="180">
          <el-table-column prop="orderNo" label="订单号" min-width="150" />
          <el-table-column prop="salesType" label="销售类型" width="90" />
          <el-table-column label="订单金额" width="150"><template #default="{ row }">{{ amountText(row.orderAmount, row.currencyCode) }}</template></el-table-column>
          <el-table-column label="下单时间" width="170"><template #default="{ row }">{{ timeText(row.orderCreateTime) }}</template></el-table-column>
          <el-table-column prop="executionNo" label="主执行单号" min-width="130" />
        </el-table>
      </div>
      <div v-if="source.executionOrders.length" class="source-block">
        <h4>关联执行单</h4>
        <el-table :data="source.executionOrders" size="small" max-height="180">
          <el-table-column prop="executionNo" label="执行单号" min-width="140" />
          <el-table-column prop="projectName" label="CRM项目名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="departmentName" label="办事处" width="120" show-overflow-tooltip />
          <el-table-column label="项目金额" width="140"><template #default="{ row }">{{ row.projectAmount ?? '—' }}</template></el-table-column>
          <el-table-column label="提交时间" width="170"><template #default="{ row }">{{ timeText(row.submitTime) }}</template></el-table-column>
        </el-table>
      </div>
    </template>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { DICT_TYPE, getDictLabel } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import type { ContractCreationSourceRespVO } from '@/api/pms/commerce'
const props = defineProps<{ source: ContractCreationSourceRespVO | null; loading: boolean; error: string; salesOrderId?: number }>()
defineEmits<{ 'select-order': [id?: number] }>()
const execution = computed(() => props.source?.executionOrders.find(item => item.id === props.source?.resolved.executionOrderId))
const mobile = useMediaQuery('(max-width: 767px)')
const dictText = (value: string | undefined, dict: DICT_TYPE) => value ? getDictLabel(dict, value) || value : '—'
const timeText = (value?: string) => value ? formatDate(value) : '—'
const amountText = (value?: number, currency?: string) => value == null ? '—' : `${currency || ''} ${value}`.trim()
</script>
<style scoped>
.source-preview { margin: 0 0 20px; }
</style>
