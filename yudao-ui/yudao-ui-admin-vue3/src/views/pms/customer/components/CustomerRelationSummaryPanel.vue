<template>
  <section class="customer-section">
    <div class="customer-section-heading"
      ><h3>{{ kind === 'project' ? '关联项目' : '关联设备' }}</h3
      ><span>仅展示当前可见的关联摘要</span></div
    >
    <el-alert
      v-if="!slice.available"
      type="warning"
      :closable="false"
      show-icon
      title="来源摘要暂不可用"
    />
    <template v-else>
      <el-form label-position="top" disabled>
        <el-form-item label="数据截止时间"
          ><el-input :model-value="formatNullableDate(slice.dataAsOf)"
        /></el-form-item>
      </el-form>
      <el-empty
        v-if="!slice.items.length"
        :image-size="80"
        :description="kind === 'project' ? '暂无关联项目' : '暂无关联设备'"
      />
      <el-form
        v-for="(item, index) in slice.items"
        :key="index"
        label-position="top"
        disabled
        class="customer-record"
      >
        <div class="customer-record-label"
          >{{ kind === 'project' ? '项目' : '设备' }} {{ index + 1 }}</div
        >
        <div class="customer-field-grid">
          <el-form-item label="编码"
            ><el-input
              :model-value="String(item[kind === 'project' ? 'projectCode' : 'deviceCode'] || '—')"
          /></el-form-item>
          <el-form-item label="名称"
            ><el-input
              :model-value="String(item[kind === 'project' ? 'projectName' : 'deviceName'] || '—')"
          /></el-form-item>
          <el-form-item label="状态"
            ><el-input :model-value="String(item.status || '—')"
          /></el-form-item>
        </div>
      </el-form>
    </template>
  </section>
</template>
<script setup lang="ts">
import type { CustomerRelationSummarySlice } from '@/api/pms/customer'
import { formatNullableDate } from '@/utils/formatTime'
defineProps<{ slice: CustomerRelationSummarySlice; kind: 'project' | 'device' }>()
</script>
