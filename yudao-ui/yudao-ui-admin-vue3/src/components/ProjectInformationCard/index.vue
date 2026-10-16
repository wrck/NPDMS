<template>
  <section v-loading="loading" class="project-information-card" aria-label="项目信息">
    <header><h2><Icon icon="ep:location" />项目信息</h2><small>{{ hint }}</small></header>
    <div class="project-information-body">
      <slot />
      <div class="project-information-grid">
        <div v-for="field in fields" :key="field.label" class="project-information-field">
          <label>{{ field.label }}</label><span>{{ field.value || '—' }}</span>
        </div>
      </div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" class="mt-12px" />
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { getDictLabel, DICT_TYPE } from '@/utils/dict'
import type { ProjectInformation } from './types'
const props = defineProps<{ project?: ProjectInformation | null; customerServiceLevel?: string | null;
  loading?: boolean; error?: string; hint?: string }>()
const fields = computed(() => {
  const value = props.project
  return [
    { label: '项目编号', value: value?.projectCode }, { label: '客户名称', value: value?.customerName },
    { label: '项目经理', value: value?.projectManagerName }, { label: '服务经理', value: value?.serviceManagerNames },
    { label: '所属行业', value: value?.industryName }, { label: '办事处', value: value?.departmentName },
    { label: '项目级别', value: value?.majorProjectLevel ? getDictLabel(DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL, value.majorProjectLevel) || value.majorProjectLevel : '' },
    { label: '客户服务等级', value: value ? props.customerServiceLevel || '三级普通（默认）' : '' },
    { label: '销售', value: value?.salesNames }
  ]
})
</script>
<style scoped>
.project-information-card { min-width: 0; margin-bottom: 16px; border-radius: 8px; background: var(--el-bg-color, #fff); box-shadow: 0 1px 3px rgb(0 0 0 / 4%); }
header { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 48px; padding: 12px 20px; border-bottom: 1px solid var(--el-border-color-light); }
h2 { display: flex; align-items: center; gap: 8px; margin: 0; color: var(--el-text-color-primary); font-size: 14px; font-weight: 600; }
small { color: var(--el-text-color-secondary); }
.project-information-body { padding: 20px; }
.project-information-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px 16px; }
.project-information-field { min-width: 0; }
label { display: block; margin-bottom: 4px; color: var(--el-text-color-secondary); font-size: 12px; }
.project-information-field span { display: block; min-height: 32px; padding: 4px 10px; overflow-wrap: anywhere; border: 1px solid var(--el-border-color); border-radius: 4px; }
@media (width <= 1023px) { .project-information-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (width <= 767px) { header { flex-wrap: wrap; padding: 12px 14px; } .project-information-body { padding: 14px; } .project-information-grid { grid-template-columns: minmax(0, 1fr); } }
</style>
