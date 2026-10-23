<template>
  <section v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
      <el-button link @click="load">重新加载</el-button>
    </el-alert>
    <template v-else-if="current">
      <div class="software-actions">
        <span>登记时间：{{ date(current.recordedAt) }}</span>
        <el-button @click="load">刷新</el-button>
        <el-button v-if="projectId" v-hasPermi="['pms:device-log-archive:query']" @click="logArchiveVisible = true">项目日志档案</el-button>
        <el-button v-if="current.canMaintain && projectId" :disabled="loading || saving" type="primary" @click="openForm"
          >维护软件版本</el-button
        >
      </div>
      <DeviceReadOnlyFields title="当前软件版本" :fields="valueFields(current.values)" />
      <DeviceSoftwareSourceHistoryPanel ref="historyPanel" :device-id="deviceId" />
    </template>
    <el-dialog
      v-model="formVisible"
      title="维护软件版本"
      width="min(650px, 95vw)"
      :close-on-click-modal="false"
      :close-on-press-escape="!saving"
      :show-close="!saving"
    >
      <el-form :model="form" label-width="110px" @submit.prevent="save">
        <el-form-item v-for="field in stringFields" :key="field.key" :label="field.label"
          ><el-input v-model="form[field.key]" :maxlength="field.max" :disabled="saving"
        /></el-form-item>
        <el-form-item label="定制标记"
          ><el-select v-model="form.customized" :disabled="saving" clearable placeholder="未记录"
            ><el-option label="是" :value="true" /><el-option
              label="否"
              :value="false" /></el-select
        ></el-form-item>
        <el-form-item label="发布日期"
          ><el-date-picker
            v-model="form.releaseDate"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled="saving"
        /></el-form-item>
        <el-form-item label="版本说明"
          ><el-input
            v-model="form.versionDesc"
            type="textarea"
            :maxlength="1000"
            :disabled="saving"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button :disabled="saving" @click="formVisible = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save">保存</el-button></template
      >
    </el-dialog>
  </section>
  <ProjectDeviceLogArchiveDialog v-if="projectId" v-model="logArchiveVisible" :project-id="projectId" :readonly="!current?.canMaintain" />
</template>
<script setup lang="ts">
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getSoftwareVersionCurrent,
  registerSoftwareVersion,
  type SoftwareVersionCurrent,
  type SoftwareVersionValues
} from '@/api/pms/asset/device'
import DeviceReadOnlyFields from './DeviceReadOnlyFields.vue'
import DeviceSoftwareSourceHistoryPanel from './DeviceSoftwareSourceHistoryPanel.vue'
import ProjectDeviceLogArchiveDialog from '@/components/DeviceCollection/ProjectDeviceLogArchiveDialog.vue'
import { formatSourceDate } from '@/utils/formatSourceDate'
const props = defineProps<{ deviceId: number | string; projectId?: number | string }>()
const emit = defineEmits<{ updated: [] }>()
const coreFields = [
  { key: 'conpVersion', label: 'CONP' },
  { key: 'bootVersion', label: 'BOOT' },
  { key: 'cpldVersion', label: 'CPLD' },
  { key: 'pcbVersion', label: 'PCB' }
] as const
const stringFields = [
  { key: 'conpVersion', label: 'CONP', max: 255 },
  { key: 'conpType', label: 'CONP类型', max: 100 },
  { key: 'conpSeries', label: 'CONP系列', max: 100 },
  { key: 'conpMark', label: 'CONP标记', max: 255 },
  { key: 'bootVersion', label: 'BOOT', max: 255 },
  { key: 'cpldVersion', label: 'CPLD', max: 255 },
  { key: 'pcbVersion', label: 'PCB', max: 255 }
] as const
const date = (value: unknown) => formatSourceDate(value, 'effectiveFrom') ?? '未记录'
const valueFields = (value?: SoftwareVersionValues | null) => [
  ...stringFields.map((field) => ({
    label: field.label,
    value: value?.[field.key] === '' ? '空字符串' : value?.[field.key]
  })),
  { label: '定制标记', value: value?.customized },
  { label: '发布日期', value: value?.releaseDate },
  { label: '版本说明', value: value?.versionDesc }
]
const current = ref<SoftwareVersionCurrent>()
const historyPanel = ref<InstanceType<typeof DeviceSoftwareSourceHistoryPanel>>()
const loading = ref(false),
  saving = ref(false),
  error = ref('')
const formVisible = ref(false)
const logArchiveVisible = ref(false)
const form = reactive<SoftwareVersionValues>({})
let generation = 0,
  requestKey = '',
  expectedVersion = 0
let active = true
const load = async () => {
  const version = ++generation
  loading.value = true
  error.value = ''
  try {
    const nextCurrent = await getSoftwareVersionCurrent(props.deviceId)
    if (version !== generation) return
    current.value = nextCurrent
    historyPanel.value?.reload()
  } catch {
    if (version === generation) error.value = '软件版本加载失败，请重试'
  } finally {
    if (version === generation) loading.value = false
  }
}
const openForm = () => {
  for (const field of stringFields) form[field.key] = current.value?.values?.[field.key] ?? null
  form.customized = current.value?.values?.customized ?? null
  form.releaseDate = current.value?.values?.releaseDate ?? null
  form.versionDesc = current.value?.values?.versionDesc ?? null
  expectedVersion = current.value!.version
  requestKey = crypto.randomUUID()
  formVisible.value = true
}
const save = async () => {
  if (saving.value || !props.projectId) return
  if (coreFields.every((field) => !form[field.key]?.trim())) {
    ElMessage.warning('至少填写一个软件版本')
    return
  }
  saving.value = true
  const deviceId = props.deviceId
  const projectId = props.projectId
  try {
    await registerSoftwareVersion(deviceId, {
      projectId,
      expectedVersion,
      requestKey,
      values: { ...form }
    })
    if (!active || deviceId !== props.deviceId || projectId !== props.projectId) return
    formVisible.value = false
    ElMessage.success('软件版本已保存')
    await load()
    if (active && deviceId === props.deviceId && projectId === props.projectId) emit('updated')
  } catch {
    if (active && deviceId === props.deviceId && projectId === props.projectId) {
      ElMessage.error('软件版本保存失败，请核对提示后重试')
    }
  } finally {
    saving.value = false
  }
}
watch(
  () => [props.deviceId, props.projectId],
  () => {
    current.value = undefined
    formVisible.value = false
    logArchiveVisible.value = false
    void load()
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  active = false
  generation++
})
</script>
<style scoped>
.software-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
</style>
