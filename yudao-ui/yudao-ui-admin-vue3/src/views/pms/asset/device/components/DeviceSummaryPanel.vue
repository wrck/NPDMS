<template>
  <DeviceReadOnlyFields
    title="设备与归属"
    :fields="[
      { label: '设备序列号', value: summary.sn },
      { label: '产品', value: productText },
      { label: '所属公司', value: organization?.companyName || '待补齐' },
      {
        label: '所属部门',
        value: organization?.departmentName || organization?.departmentCode || '待补齐'
      },
      {
        label: '归属来源',
        value:
          organization?.source === 'PROJECT'
            ? '项目'
            : organization?.source === 'CONTRACT'
              ? '合同'
              : '待补齐'
      },
      { label: '当前项目', value: summary.projectId },
      { label: '当前客户', value: summary.customerId },
      { label: '当前位置', value: '请在位置与变更轨迹页签查看' },
      { label: '待核对', value: reconciliationText },
      { label: '项目归属版本', value: summary.projectAssignmentVersion },
      { label: '客户归属版本', value: summary.customerAssignmentVersion }
    ]"
  />
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { DeviceSummaryVO, DeviceOrganizationVO } from '@/api/pms/asset/device'
import DeviceReadOnlyFields from './DeviceReadOnlyFields.vue'
const props = defineProps<{ summary: DeviceSummaryVO; organization?: DeviceOrganizationVO }>()
const productText = computed(() =>
  [props.summary.productCode, props.summary.productModel, props.summary.productName]
    .filter(Boolean)
    .join(' / ')
)
const reconciliationText = computed(() =>
  props.summary.projectId && props.summary.customerId ? '以服务端核对状态为准' : '无待核对事实'
)
</script>
