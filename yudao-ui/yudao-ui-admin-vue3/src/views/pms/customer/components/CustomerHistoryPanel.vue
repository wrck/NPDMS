<template>
  <section class="customer-section">
    <div class="customer-section-heading"
      ><h3>变更记录</h3><span>保留原始变更顺序及字段摘要</span></div
    >
    <el-empty v-if="!history.length" :image-size="80" description="暂无变更历史" />
    <el-form
      v-for="item in history"
      :key="item.operationId"
      label-position="top"
      disabled
      class="customer-record"
    >
      <div class="customer-record-label">{{ formatNullableDate(item.occurredAt) }}</div>
      <div class="customer-field-grid">
        <el-form-item label="变更字段"><el-input :model-value="item.fieldName" /></el-form-item>
        <el-form-item label="字段归属"><el-input :model-value="item.fieldOwner" /></el-form-item>
        <el-form-item label="变更前"
          ><el-input :model-value="item.beforeValueDigest || '—'" type="textarea" :rows="3"
        /></el-form-item>
        <el-form-item label="变更后"
          ><el-input :model-value="item.afterValueDigest || '—'" type="textarea" :rows="3"
        /></el-form-item>
      </div>
    </el-form>
  </section>
</template>
<script setup lang="ts">
import type { CustomerHistoryVO } from '@/api/pms/customer'
import { formatNullableDate } from '@/utils/formatTime'
defineProps<{ history: CustomerHistoryVO[] }>()
</script>
