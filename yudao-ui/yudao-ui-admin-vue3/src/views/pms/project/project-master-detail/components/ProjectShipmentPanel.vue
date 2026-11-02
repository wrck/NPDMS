<template>
  <section aria-label="项目发货设备">
    <p class="mb-12px"
      >按项目关联合同展示发货记录；设备当前归属请查看“归属设备”。共 {{ total }} 条发货记录。</p
    >
    <el-form inline @submit.prevent="search">
      <el-form-item label="序列号"
        ><el-input v-model="sn" clearable maxlength="100" placeholder="请输入序列号"
      /></el-form-item>
      <el-form-item
        ><el-button native-type="submit" type="primary" :loading="loading"
          >查询</el-button
        ></el-form-item
      >
    </el-form>
    <el-alert
      v-if="error"
      title="发货设备加载失败，请重新查询。"
      type="error"
      :closable="false"
      class="mb-12px"
    />
    <el-table
      v-loading="loading"
      :data="rows"
      row-key="id"
      empty-text="暂无符合条件的项目发货记录"
      data-testid="project-shipment-table"
    >
      <el-table-column prop="deviceSn" label="设备序列号" min-width="180" show-overflow-tooltip />
      <el-table-column prop="productCode" label="产品编码" min-width="150" show-overflow-tooltip />
      <el-table-column prop="contractNo" label="合同号" min-width="180" show-overflow-tooltip />
      <el-table-column prop="orderNo" label="订单号" min-width="140" />
      <el-table-column prop="lineNo" label="订单行号" width="110" />
      <el-table-column prop="shipmentTime" label="发货时间" min-width="180">
        <template #default="{ row }">{{
          row.shipmentTime == null ? '—' : formatDate(row.shipmentTime).replace(/ 00:00:00$/, '')
        }}</template>
      </el-table-column>
      <el-table-column prop="carrierName" label="快递公司" min-width="130" show-overflow-tooltip />
      <el-table-column prop="expressNo" label="快递单号" min-width="180" show-overflow-tooltip />
      <el-table-column prop="receiverName" label="收件人" min-width="120" show-overflow-tooltip />
      <el-table-column prop="warrantyStartDate" label="维保开始日期" width="130">
        <template #default="{ row }">{{
          row.warrantyStartDate == null ? '—' : formatDate(row.warrantyStartDate, 'YYYY-MM-DD')
        }}</template>
      </el-table-column>
      <el-table-column label="维保月数" width="100"
        ><template #default="{ row }">{{ row.warrantyMonths ?? '—' }}</template></el-table-column
      >
    </el-table>
    <Pagination v-model:page="pageNo" v-model:limit="pageSize" :total="total" @pagination="load" />
  </section>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { formatDate } from '@/utils/formatTime'
import { getProjectShipments, type ProjectShipment } from '@/api/pms/asset/project-shipment'
const props = defineProps<{ projectId: number; active: boolean }>()
const rows = ref<ProjectShipment[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const sn = ref('')
const loading = ref(false)
const error = ref(false)
let sequence = 0
const load = async () => {
  const current = ++sequence
  rows.value = []
  error.value = false
  if (!props.active || !props.projectId) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const page = await getProjectShipments({
      projectId: props.projectId,
      sn: sn.value.trim() || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value
    })
    if (current !== sequence) return
    rows.value = page.list
    total.value = Number(page.total)
  } catch {
    if (current === sequence) {
      error.value = true
      total.value = 0
    }
  } finally {
    if (current === sequence) loading.value = false
  }
}
const search = () => {
  pageNo.value = 1
  void load()
}
watch(
  () => props.projectId,
  () => {
    pageNo.value = 1
    total.value = 0
    sn.value = ''
    void load()
  },
  { immediate: true }
)
watch(
  () => props.active,
  () => {
    void load()
  }
)
onBeforeUnmount(() => {
  ++sequence
})
</script>
