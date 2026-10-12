<template>
  <DeviceSoftwareVersionMaintenancePanel
    v-if="deviceId"
    :device-id="deviceId"
    :project-id="projectId"
    @updated="$emit('updated')"
  />
  <DeviceParsedLogRecords v-if="deviceId" :device-id="deviceId" />
  <template v-else>
    <DeviceSourceStatus :slice="slice" />
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
  </template>
</template>
<script setup lang="ts">
import DeviceSoftwareVersionMaintenancePanel from './DeviceSoftwareVersionMaintenancePanel.vue'
import DeviceParsedLogRecords from './DeviceParsedLogRecords.vue'
import { computed } from 'vue'
import type { DeviceSourceSliceVO } from '@/api/pms/asset/device'
import DeviceSourceStatus from './DeviceSourceStatus.vue'
import DeviceReadOnlyFields from './DeviceReadOnlyFields.vue'
const props = defineProps<{ slice: DeviceSourceSliceVO; deviceId?: number; projectId?: number }>()
defineEmits<{ updated: [] }>()
const version = computed(() => (props.slice.data || {}) as Record<string, unknown>)
</script>
