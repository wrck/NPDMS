<template>
  <DeviceSourceStatus :slice="slice" />
  <p class="text-sm text-[var(--el-text-color-secondary)]"
    >在网版本来自设备最新日志的有效解析结果。</p
  >
  <DeviceReadOnlyFields
    title="在网版本"
    :fields="[
      { label: 'CONP原始版本', value: version.conpVersion },
      { label: 'CONP类型', value: version.conpType },
      { label: 'CONP系列', value: version.conpSeries },
      { label: 'CONP标记', value: version.conpMark },
      { label: 'BOOT', value: version.bootVersion },
      { label: 'CPLD', value: version.cpldVersion },
      { label: 'PCB', value: version.pcbVersion },
      { label: '定制版本', value: version.customized }
    ]"
  />
  <DeviceParsedLogRecords
    v-if="deviceId && slice.sourceSystem === 'DEVICE_LOG'"
    :device-id="deviceId"
  />
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { DeviceSourceSliceVO } from '@/api/pms/asset/device'
import DeviceSourceStatus from './DeviceSourceStatus.vue'
import DeviceReadOnlyFields from './DeviceReadOnlyFields.vue'
import DeviceParsedLogRecords from './DeviceParsedLogRecords.vue'
const props = defineProps<{ slice: DeviceSourceSliceVO; deviceId?: number | string }>()
const version = computed(() => (props.slice.data || {}) as Record<string, unknown>)
</script>
