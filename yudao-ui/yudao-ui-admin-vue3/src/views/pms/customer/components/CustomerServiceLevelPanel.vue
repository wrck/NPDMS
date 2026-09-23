<template>
  <section class="customer-section">
    <div class="customer-section-heading">
      <h3>服务等级</h3>
      <span>按生效区间维护的等级与响应承诺，含历史记录</span>
    </div>
    <el-table v-loading="loading" :data="rows" empty-text="该客户尚未配置服务等级">
      <el-table-column label="服务等级" width="120">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_SERVICE_LEVEL" :value="row.level" />
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_SRV_LEVEL_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="validFrom" label="生效开始" width="120">
        <template #default="{ row }">{{ row.validFrom || '—' }}</template>
      </el-table-column>
      <el-table-column prop="validTo" label="生效结束" width="120">
        <template #default="{ row }">{{ row.validTo || '—' }}</template>
      </el-table-column>
      <el-table-column prop="responseTimeHours" label="响应时间(h)" width="110">
        <template #default="{ row }">{{ row.responseTimeHours ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="主动服务" width="90">
        <template #default="{ row }">
          <el-tag :type="row.proactiveService ? 'success' : 'info'" size="small">{{
            row.proactiveService ? '是' : '否'
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '—' }}</template>
      </el-table-column>
    </el-table>
    <div v-if="loadFailed" class="mt-8px text-12px text-[var(--el-text-color-secondary)]"
      >服务等级信息需要服务等级查询权限。</div
    >
    <div v-else-if="!loading && !rows.length" class="mt-8px text-12px text-[var(--el-text-color-secondary)]"
      >可在“客户服务等级”功能中为该客户维护等级记录。</div
    >
  </section>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { DICT_TYPE } from '@/utils/dict'
import * as ServiceLevelApi from '@/api/pms/customer/service-level'
import type { CustomerServiceLevelVO } from '@/api/pms/customer/service-level'
import type { CustomerDetailRespVO } from '@/api/pms/customer'

const props = defineProps<{ customer: CustomerDetailRespVO }>()
const rows = ref<CustomerServiceLevelVO[]>([])
const loading = ref(false)
const loadFailed = ref(false)

const build = async () => {
  loading.value = true
  loadFailed.value = false
  rows.value = []
  try {
    const page = await ServiceLevelApi.getServiceLevelPage({
      pageNo: 1,
      pageSize: 50,
      customerId: props.customer.id
    })
    rows.value = page.list || []
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

watch(
  () => props.customer.id,
  () => {
    void build()
  },
  { immediate: true }
)
</script>
