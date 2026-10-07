<template>
  <div class="default-delivery-records">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table :data="rows" v-loading="loading" row-key="id">
      <el-table-column prop="deliverableType" label="交付件类型" />
      <el-table-column prop="businessType" label="业务类型" />
      <el-table-column prop="businessEntityKey" label="业务实体键" />
      <el-table-column label="标题"><template #default="{ row }">
        <el-input v-if="!readonly" v-model="titles[row.id]" maxlength="255" :aria-label="`标题-${row.id}`" />
        <span v-else>{{ row.title }}</span>
      </template></el-table-column>
      <el-table-column label="文件"><template #default="{ row }"><el-button link @click="download(row)">{{ row.fileName }}</el-button></template></el-table-column>
      <el-table-column label="上传时间"><template #default="{ row }">{{ new Date(row.uploadedAt).toLocaleString() }}</template></el-table-column>
      <el-table-column v-if="!readonly" label="操作"><template #default="{ row }">
        <el-button link :disabled="loading" @click="edit(row)">保存标题</el-button>
        <el-button link type="danger" :disabled="loading" @click="remove(row)">删除</el-button>
      </template></el-table-column>
    </el-table>
    <el-button :disabled="loading" @click="load">刷新交付件</el-button>
    <el-button v-if="pageNo > 1" :disabled="loading" @click="pageNo--; load()">上一页</el-button>
    <el-button v-if="rows.length === 20" :disabled="loading" @click="pageNo++; load()">下一页</el-button>
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import * as api from '@/api/pms/platform/businessmodel/delivery'
const props = defineProps<{ projectId: string | number; deliverableType?: string; businessType?: string; businessEntityKey?: string | number; readonly?: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const rows = ref<api.DeliveryRecord[]>([]), titles = ref<Record<string, string>>({}), loading = ref(false), error = ref(''), pageNo = ref(1)
let generation = 0
const load = async () => {
  const active = ++generation; loading.value = true; error.value = ''; rows.value = []
  try {
    const result = await api.listDeliveries({ projectId: props.projectId, deliverableType: props.deliverableType,
      businessType: props.businessType, businessEntityKey: props.businessEntityKey, pageNo: pageNo.value, pageSize: 20 })
    if (active === generation) { rows.value = result.list; titles.value = Object.fromEntries(result.list.map(row => [row.id, row.title])) }
  } catch (failure: any) { if (active === generation) error.value = failure?.message || '交付件读取失败' }
  finally { if (active === generation) loading.value = false }
}
const download = async (row: api.DeliveryRecord) => {
  try { const ticket = await api.downloadDelivery(row); window.open(ticket.shortLivedUrl, '_blank', 'noopener') }
  catch (failure: any) { error.value = failure?.message || '文件读取失败' }
}
const edit = async (row: api.DeliveryRecord) => {
  if (props.readonly || loading.value) return
  loading.value = true; error.value = ''
  try { await api.editDelivery(row, titles.value[row.id]); await load(); emit('changed') }
  catch (failure: any) { error.value = failure?.message || '保存失败'; loading.value = false }
}
const remove = async (row: api.DeliveryRecord) => {
  if (props.readonly || loading.value) return
  loading.value = true; error.value = ''
  try { await api.deleteDelivery(row); await load(); emit('changed') }
  catch (failure: any) { error.value = failure?.message || '删除失败'; loading.value = false }
}
watch(() => [props.projectId, props.deliverableType, props.businessType, props.businessEntityKey], () => { pageNo.value = 1; load() }, { immediate: true })
defineExpose({ reload: load })
</script>
