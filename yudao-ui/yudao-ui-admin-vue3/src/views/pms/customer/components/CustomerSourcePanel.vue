<template>
  <el-form label-position="top" disabled>
    <section class="customer-section">
      <div class="customer-section-heading"
        ><h3>来源信息</h3><span>客户来源、同步及对账状态</span></div
      >
      <div class="customer-field-grid">
        <el-form-item label="来源"><el-input :model-value="sourceTypeLabel" /></el-form-item>
        <el-form-item label="同步状态"
          ><el-input :model-value="customer.syncStatus || '—'"
        /></el-form-item>
        <el-form-item label="对账状态"
          ><el-input :model-value="customer.reconciliationPending ? '待对账' : '无需对账'"
        /></el-form-item>
        <el-form-item
          v-if="customer.sourceType === 'PLATFORM_TEMPORARY'"
          label="临时客户原因"
          class="customer-field-wide"
        >
          <el-input :model-value="customer.temporaryReason || '—'" type="textarea" :rows="3" />
        </el-form-item>
      </div>
    </section>
    <section class="customer-section">
      <div class="customer-section-heading"
        ><h3>联系方式</h3><span>仅展示当前权限范围内的联系信息</span></div
      >
      <div class="customer-field-grid">
        <el-form-item label="联系电话"
          ><el-input :model-value="customer.contactPhone || '—'"
        /></el-form-item>
        <el-form-item label="联系邮箱"
          ><el-input :model-value="customer.contactEmail || '—'"
        /></el-form-item>
      </div>
    </section>
  </el-form>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { CustomerDetailRespVO } from '@/api/pms/customer'
const props = defineProps<{ customer: CustomerDetailRespVO }>()
const sourceTypeLabel = computed(
  () =>
    ({
      CRM_SYNC: 'CRM 同步',
      PLATFORM_CREATED: '平台创建',
      PLATFORM_TEMPORARY: '平台临时'
    })[props.customer.sourceType]
)
</script>
