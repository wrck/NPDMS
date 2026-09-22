<template>
  <section class="customer-section">
    <div class="customer-section-heading"><h3>地点引用</h3><span>查看地点来源及生效信息</span></div>
    <el-empty v-if="!locations.length" :image-size="80" description="暂无地点引用" />
    <el-form
      v-for="(item, index) in locations"
      :key="`${item.locationType}-${item.locationId}-${index}`"
      label-position="top"
      disabled
      class="customer-record"
    >
      <div class="customer-record-label">地点 {{ index + 1 }}</div>
      <div class="customer-field-grid">
        <el-form-item label="地点类型"><el-input :model-value="item.locationType" /></el-form-item>
        <el-form-item label="稳定引用 ID"
          ><el-input :model-value="String(item.locationId)"
        /></el-form-item>
        <el-form-item label="来源版本"><el-input :model-value="item.sourceVersion" /></el-form-item>
        <el-form-item label="生效时间"
          ><el-input :model-value="formatNullableDate(item.effectiveFrom)"
        /></el-form-item>
      </div>
    </el-form>
  </section>
</template>
<script setup lang="ts">
import type { CustomerLocationVO } from '@/api/pms/customer'
import { formatNullableDate } from '@/utils/formatTime'
defineProps<{ locations: CustomerLocationVO[] }>()
</script>
