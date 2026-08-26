<template>
  <el-empty v-if="!locations.length" description="暂无地点引用" />
  <el-table v-else :data="locations">
    <el-table-column prop="locationType" label="地点类型" />
    <el-table-column prop="locationId" label="稳定引用 ID" />
    <el-table-column prop="sourceVersion" label="来源版本" />
    <el-table-column prop="effectiveFrom" label="生效时间" />
  </el-table>
</template>

<script setup lang="ts">
import type { CustomerLocationVO } from '@/api/pms/customer'

defineProps<{ locations: CustomerLocationVO[] }>()
</script>
