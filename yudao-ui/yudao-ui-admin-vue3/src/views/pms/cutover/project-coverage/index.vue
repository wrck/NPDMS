<template>
  <section v-loading="loading">
    <el-card shadow="never" class="mb-4">
      <template #header>
        <div class="flex items-center justify-between gap-3">
          <strong>项目割接范围覆盖</strong>
          <el-button :disabled="!project || loading" @click="load">刷新</el-button>
        </div>
      </template>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <template v-else-if="coverage">
        <el-alert :type="coverage.complete ? 'success' : 'info'" :closable="false"
          :title="coverage.complete ? '当前实施设备范围已全部割接成功' : '当前实施设备范围尚未全部割接成功'"
          :description="`已覆盖 ${coverage.coveredDeviceIds.length} / ${coverage.requiredDevices.length} 台设备；按当前设备归属版本核对成功归档结果。`" />
        <el-table :data="coverage.requiredDevices" max-height="320" class="mt-3" empty-text="当前没有可判定的实施设备范围">
          <el-table-column prop="serialNumber" label="设备序列号" min-width="200" />
          <el-table-column label="当前范围结果" min-width="160">
            <template #default="{ row }"><el-tag :type="covered(row.deviceId) ? 'success' : 'warning'">{{ covered(row.deviceId) ? '已成功归档' : '待成功割接' }}</el-tag></template>
          </el-table-column>
        </el-table>
      </template>
    </el-card>
    <CutoverWorkbench v-if="project" :project-id="project" :readonly="true" :allowed-actions="['QUERY']" />
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import CutoverWorkbench from '../workbench/index.vue'
import { getProjectCoverage, type CutoverResultProgress, type WireLong } from '@/api/pms/cutover/workbench'
defineOptions({ name: 'ProjectCutoverCoverage' })
const props = defineProps<{ projectId?: WireLong }>()
const route = useRoute()
const project = computed(() => props.projectId ?? (typeof route.query.projectId === 'string' && /^[1-9][0-9]*$/.test(route.query.projectId) ? route.query.projectId : undefined))
const coverage = ref<CutoverResultProgress['projectCoverage']>()
const loading = ref(false)
const error = ref('')
const covered = (id: WireLong) => coverage.value?.coveredDeviceIds.some(value => String(value) === String(id))
const load = async () => {
  coverage.value = undefined
  if (!project.value) { error.value = '缺少项目上下文'; return }
  loading.value = true
  error.value = ''
  const requested = project.value
  try {
    const value = await getProjectCoverage(requested)
    if (String(project.value) === String(requested)) coverage.value = value
  } catch (failure: any) {
    if (String(project.value) === String(requested)) error.value = failure?.message || '项目割接覆盖事实暂不可用，请稍后重试'
  } finally { if (String(project.value) === String(requested)) loading.value = false }
}
watch(project, load, { immediate: true })
</script>
