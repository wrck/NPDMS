<template>
  <div class="planning-import">
    <el-button size="small" :disabled="readOnly || loading" :loading="loading" @click="input?.click()">导入并追加</el-button>
    <input ref="input" type="file" accept=".xlsx,.csv" hidden :disabled="readOnly || loading" @change="importFile" />
    <el-button link type="primary" @click="downloadTemplate">下载XLSX模板</el-button>
    <p v-if="error" class="import-error" role="alert">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import download from '@/utils/download'
import { parsePlanningImport, type PlanningSection } from './solutionPlanningImport'
import { planningWorkbook, parsePlanningWorkbook } from './solutionPlanningWorkbook'

const props = defineProps<{ section: PlanningSection; readOnly: boolean }>()
const emit = defineEmits<{ imported: [rows: Record<string, string>[]] }>()
const input = ref<HTMLInputElement>()
const loading = ref(false)
const error = ref('')
const downloadTemplate = async () => {
  error.value = ''
  try {
    const section = props.section
    const data = await planningWorkbook(section)
    download.file(new Blob([new Uint8Array(data).buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }),
      `${section === 'interface' ? '接口互联规划' : 'IP与VLAN规划'}导入模板.xlsx`)
  } catch {
    error.value = '模板下载失败，请重试。'
  }
}
const importFile = async () => {
  const file = input.value?.files?.[0]
  if (!file || props.readOnly || loading.value) return
  loading.value = true
  error.value = ''
  const section = props.section
  try {
    const name = file.name.toLowerCase()
    if (!/\.(xlsx|csv)$/.test(name)) throw new Error('请选择XLSX或CSV UTF-8文件。')
    const rows = name.endsWith('.xlsx')
      ? await parsePlanningWorkbook(section, await file.arrayBuffer())
      : parsePlanningImport(section, await file.text())
    if (!props.readOnly && props.section === section) emit('imported', rows)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '导入失败，请检查文件。'
  } finally {
    loading.value = false
    if (input.value) input.value.value = ''
  }
}
</script>

<style scoped>
.planning-import { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 8px; }
.import-error { width: 100%; margin: 0; color: var(--el-color-danger); }
</style>
