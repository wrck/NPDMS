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
      <el-descriptions v-if="salesOrderId" title="来源基本信息" :column="2" border size="small">
        <el-descriptions-item label="订单项目名称">{{ source.resolved.projectName ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="关联执行单">{{ source.resolved.executionNo ?? '未关联' }}</el-descriptions-item>
        <el-descriptions-item label="订单购货方">{{ source.resolved.customerName ?? '—' }}（{{ source.resolved.customerCode ?? '—' }}）</el-descriptions-item>
        <el-descriptions-item label="来源最终客户">当前来源接口未提供</el-descriptions-item>
        <el-descriptions-item label="客户项目名称">{{ source.resolved.customerProjectName ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="重大项目级别">{{ source.resolved.majorProjectLevel ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ source.resolved.orderCreateTime ? formatDate(source.resolved.orderCreateTime) : '—' }}</el-descriptions-item>
        <el-descriptions-item label="销售代表">{{ execution?.salesRepName ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="市场 / 系统">{{ source.resolved.marketName ?? '—' }} / {{ source.resolved.systemName ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="拓展 / 行业">{{ source.resolved.expendName ?? '—' }} / {{ source.resolved.industryName ?? '—' }}</el-descriptions-item>
      </el-descriptions>
    </template>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { formatDate } from '@/utils/formatTime'
import type { ContractCreationSourceRespVO } from '@/api/pms/commerce'
const props = defineProps<{ source: ContractCreationSourceRespVO | null; loading: boolean; error: string; salesOrderId?: number }>()
defineEmits<{ 'select-order': [id?: number] }>()
const execution = computed(() => props.source?.executionOrders.find(item => item.id === props.source?.resolved.executionOrderId))
</script>
<style scoped>
.source-preview { margin: 0 0 20px; }
</style>
