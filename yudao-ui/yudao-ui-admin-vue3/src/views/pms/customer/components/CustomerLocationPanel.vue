<template>
  <el-empty v-if="!locations.length" description="暂无地点引用" />
  <el-table v-else :data="locations">
    <el-table-column prop="locationType" label="地点类型" />
    <el-table-column prop="locationId" label="稳定引用 ID" />
    <el-table-column prop="sourceVersion" label="来源版本" />
    <el-table-column label="生效时间">
      <template #default="{ row }">{{ formatNullableDate(row.effectiveFrom) }}</template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import type { CustomerLocationVO } from '@/api/pms/customer'
import { formatNullableDate } from '@/utils/formatTime'

defineProps<{ locations: CustomerLocationVO[] }>()
</script>
