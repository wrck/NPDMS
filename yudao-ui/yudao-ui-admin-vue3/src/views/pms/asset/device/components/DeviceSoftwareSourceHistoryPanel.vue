<template>
  <section class="source-history" v-loading="loading">
    <h3>软件版本历史</h3>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
      <el-button link type="primary" @click="load">重新加载</el-button>
    </el-alert>
    <el-table v-else-if="rows.length" :data="rows" row-key="record.id">
      <el-table-column label="登记时间" min-width="175"
        ><template #default="{ row }">{{ date(row.record.recordedAt) }}</template></el-table-column
      >
      <el-table-column label="登记来源" min-width="110"
        ><template #default="{ row }">{{
          source(row.record.dataSource)
        }}</template></el-table-column
      >
      <el-table-column
        v-for="field in coreFields"
        :key="field.key"
        :prop="`record.${field.key}`"
        :label="field.label"
        min-width="130"
        show-overflow-tooltip
      />
      <el-table-column label="操作" width="100" fixed="right"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="selected = row">查看与比较</el-button></template
        ></el-table-column
      >
    </el-table>
    <el-empty v-else-if="!loading" :image-size="70" description="暂无软件登记记录" />
    <el-pagination
      v-if="total > 0"
      v-model:current-page="pageNo"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[20, 50, 100]"
      layout="total, sizes, prev, pager, next"
      @size-change="reload"
      @current-change="load"
    />
    <el-drawer
      :model-value="!!selected"
      title="软件版本登记与比较"
      size="min(900px, 95vw)"
      destroy-on-close
      @close="selected = undefined"
    >
      <template v-if="selected">
        <DeviceReadOnlyFields title="本次完整版本" :fields="versionFields" />
        <el-table v-if="selected.previous" :data="comparison"
          ><el-table-column prop="label" label="版本项" /><el-table-column
            prop="before"
            label="登记前" /><el-table-column prop="after" label="登记后" /><el-table-column
            prop="changed"
            label="是否变化"
        /></el-table>
        <p v-else>此记录没有可见前记录，未推断历史变更。</p>
        <DeviceReadOnlyFields title="登记资料" :fields="registrationFields" />
        <DeviceReadOnlyFields
          v-if="selected.record.dataSource === 'DPPMS'"
          title="原变更前备份及原变化标志"
          :fields="legacyFields"
        />
      </template>
    </el-drawer>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { getSoftwareVersionHistory, type SoftwareVersionHistory } from '@/api/pms/asset/device'
import DeviceReadOnlyFields from './DeviceReadOnlyFields.vue'
import { formatSourceDate } from '@/utils/formatSourceDate'
const props = defineProps<{ deviceId: number }>()
const rows = ref<SoftwareVersionHistory[]>([]),
  selected = ref<SoftwareVersionHistory>()
const pageNo = ref(1),
  pageSize = ref(20),
  total = ref(0),
  loading = ref(false),
  error = ref('')
let generation = 0
const coreFields = [
  { key: 'conpVersion', label: 'CONP' },
  { key: 'bootVersion', label: 'BOOT' },
  { key: 'cpldVersion', label: 'CPLD' },
  { key: 'pcbVersion', label: 'PCB' }
] as const
const text = (value: unknown) =>
  value == null ? '未记录' : value === '' ? '空字符串' : String(value)
const date = (value: unknown, field = 'effectiveFrom') => formatSourceDate(value, field) ?? '未记录'
const sourceNames: Record<string, string> = {
  PLATFORM_MANUAL: '手工登记',
  DEVICE_LOG: '日志回填',
  DPPMS: '历史迁入',
  CURRENT_BASELINE: '初始当前值'
}
const source = (value: string) => sourceNames[value] ?? value
const versionFields = computed(() => {
  const row = selected.value?.record
  return [
    ...coreFields.map((field) => ({ label: field.label, value: text(row?.[field.key]) })),
    { label: 'CONP类型', value: text(row?.conpType) },
    { label: 'CONP系列', value: text(row?.conpSeries) },
    { label: 'CONP标记', value: text(row?.conpMark) },
    { label: '定制标记', value: row?.customized },
    { label: '发布日期', value: date(row?.releaseDate, 'executeTime') },
    { label: '版本说明', value: text(row?.versionDesc) }
  ]
})
const comparison = computed(() =>
  coreFields.map((field) => ({
    label: field.label,
    before: text(selected.value?.previous?.[field.key]),
    after: text(selected.value?.record[field.key]),
    changed:
      selected.value?.previous?.[field.key] === selected.value?.record[field.key] ? '否' : '是'
  }))
)
const registrationFields = computed(() => {
  const row = selected.value?.record
  if (!row) return []
  return [
    { label: '记录ID', value: row.id },
    { label: '设备SN', value: row.deviceSn },
    { label: '项目ID', value: row.projectId },
    { label: '前记录ID', value: row.previousRecordId },
    { label: '来源', value: source(row.dataSource) },
    { label: '登记时间', value: date(row.recordedAt) },
    { label: '变更批次', value: row.changeBatchId },
    { label: '解析记录', value: row.parsedFactId },
    { label: '合同号', value: row.contractNo },
    { label: '产品编码', value: row.productCode },
    { label: '原执行日期', value: date(row.executeTime, 'executeTime') },
    { label: '原有效标记', value: row.dataState },
    { label: '原完整JSON', value: row.customInfo, multiline: true },
    { label: '创建人', value: row.creator },
    { label: '创建时间', value: date(row.createTime) },
    { label: '更新人', value: row.updater },
    { label: '更新时间', value: date(row.updateTime) }
  ]
})
const legacyFields = computed(() =>
  ['conp', 'boot', 'cpld', 'pcb'].flatMap((key) => [
    {
      label: `${key.toUpperCase()}原备份`,
      value: text(selected.value?.legacyAttributes[`${key}_bak`])
    },
    {
      label: `${key.toUpperCase()}原变化标志`,
      value: selected.value?.legacyAttributes[`${key}_change`]
    }
  ])
)
const load = async () => {
  const request = ++generation
  loading.value = true
  error.value = ''
  try {
    const page = await getSoftwareVersionHistory(props.deviceId, {
      pageNo: pageNo.value,
      pageSize: pageSize.value
    })
    if (request !== generation) return
    rows.value = page.list || []
    total.value = page.total || 0
  } catch {
    if (request === generation) {
      rows.value = []
      total.value = 0
      error.value = '软件版本历史加载失败'
    }
  } finally {
    if (request === generation) loading.value = false
  }
}
const reload = () => {
  pageNo.value = 1
  void load()
}
watch(
  () => props.deviceId,
  () => {
    selected.value = undefined
    rows.value = []
    reload()
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  generation++
})
defineExpose({ reload })
</script>
<style scoped>
.source-history {
  margin-top: 20px;
}
</style>
