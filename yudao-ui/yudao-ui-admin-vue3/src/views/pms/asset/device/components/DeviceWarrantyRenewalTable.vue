<template>
  <section aria-label="续保记录">
    <el-form inline @submit.prevent="search">
      <el-form-item v-if="projectId" label="序列号">
        <el-input v-model="sn" clearable placeholder="查询设备序列号" @keyup.enter="search" />
      </el-form-item>
      <el-form-item
        ><el-button :loading="loading" @click="search">查询续保记录</el-button></el-form-item
      >
    </el-form>
    <el-alert
      v-if="error"
      title="续保记录加载失败，请重新查询。"
      type="error"
      :closable="false"
      class="mb-12px"
    />
    <el-table
      v-loading="loading"
      :data="rows"
      border
      stripe
      row-key="id"
      empty-text="暂无符合条件的续保记录"
      data-testid="warranty-renewal-table"
    >
      <el-table-column prop="deviceSn" label="设备序列号" min-width="180" show-overflow-tooltip />
      <el-table-column
        prop="warrantyContractNo"
        label="续保合同号"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        prop="warrantyGrade"
        label="维保等级编码"
        min-width="130"
        show-overflow-tooltip
      />
      <el-table-column prop="warrantyStartDate" label="实际开始日期" width="130" />
      <el-table-column prop="warrantyEndDate" label="实际结束日期" width="130" />
      <el-table-column label="参考月数" width="100">
        <template #default="{ row }">{{ row.warrantyMonths ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="是否延保" width="100">
        <template #default="{ row }">{{ flag(row.extended) }}</template>
      </el-table-column>
      <el-table-column label="最新选择" width="100">
        <template #default="{ row }">{{ flag(row.isLatest) }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
    </el-table>
    <Pagination v-model:page="pageNo" v-model:limit="pageSize" :total="total" @pagination="load" />
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { getWarrantyRenewals, type WarrantyRenewalRecord } from '@/api/pms/asset/warranty-renewal'

const props = defineProps<{ projectId?: number; deviceId?: number }>()
const rows = ref<WarrantyRenewalRecord[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const error = ref(false)
const sn = ref('')
let sequence = 0
const flag = (value?: boolean | null) => (value == null ? '未记录' : value ? '是' : '否')
const load = async () => {
  const current = ++sequence
  rows.value = []
  error.value = false
  loading.value = true
  try {
    const result = await getWarrantyRenewals({
      projectId: props.projectId,
      deviceId: props.deviceId,
      sn: sn.value || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value
    })
    if (current !== sequence) return
    rows.value = result.list
    total.value = Number(result.total)
  } catch {
    if (current === sequence) error.value = true
  } finally {
    if (current === sequence) loading.value = false
  }
}
const search = () => {
  pageNo.value = 1
  total.value = 0
  void load()
}
watch(
  () => [props.projectId, props.deviceId],
  () => {
    sn.value = ''
    search()
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  ++sequence
})
</script>
