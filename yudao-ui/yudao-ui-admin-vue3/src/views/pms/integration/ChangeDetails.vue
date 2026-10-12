<template>
  <el-table :data="fields" class="integration-table" empty-text="此记录没有字段明细">
    <el-table-column prop="field" label="字段" min-width="140" />
    <el-table-column prop="before" label="处理前" min-width="180" />
    <el-table-column prop="after" label="来源转换值" min-width="180" />
    <el-table-column label="差异" width="90">
      <template #default="{ row }">
        <el-tag v-if="row.changed" type="warning" size="small">有变化</el-tag>
        <span v-else>一致</span>
      </template>
    </el-table-column>
  </el-table>
  <template v-if="originalFields.length">
    <el-divider content-position="left">{{ originalFieldsTitle }}</el-divider>
    <el-table :data="originalFields" class="integration-table" aria-label="原表字段">
      <el-table-column prop="field" label="原字段" min-width="180" />
      <el-table-column prop="value" label="原值" min-width="280" />
    </el-table>
  </template>
</template>
<script setup lang="ts">
import type { Change } from '@/api/pms/integration'
import {
  dictionaryDataOriginalFields,
  deviceRelationshipOriginalFields,
  fieldChanges,
  projectContractOriginalFields
} from './changePresentation'
const props = defineProps<{ change: Change }>()
const fields = computed(() => fieldChanges(props.change))
const originalFields = computed(() =>
  props.change.object === 'fnd_basic_data'
    ? dictionaryDataOriginalFields(props.change)
    : props.change.object === 'fb_shipment_barcode_relation'
      ? deviceRelationshipOriginalFields(props.change)
      : projectContractOriginalFields(props.change)
)
const originalFieldsTitle = computed(() =>
  props.change.object === 'fnd_basic_data'
    ? '原表字段：基础字典项'
    : props.change.object === 'fb_shipment_barcode_relation'
      ? '原表字段：主附设备关系'
      : '原表字段：合同及关联桥'
)
</script>
