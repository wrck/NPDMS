<template>
  <el-drawer
    :model-value="Boolean(record)"
    title="DPPMS 资料详情"
    size="min(720px, 95vw)"
    @close="$emit('close')"
  >
    <template v-if="record">
      <p class="record-origin">{{ domain?.label }} · 来源编号 {{ record.sourceKey }}</p>
      <p role="status">业务承接：{{ legacyNativeStatusText(record.nativeStatus) }}</p>
      <p v-if="record.nativeStatus?.reason">{{ record.nativeStatus.reason }}</p>
      <p v-if="record.nativeStatus?.targets.length">
        已关联：{{
          [
            ...new Set(
              record.nativeStatus.targets.map((t) => legacyNativeTargetLabel(t.targetTable))
            )
          ].join('、')
        }}
      </p>
      <div class="related-actions">
        <el-button v-if="record.parentDomain" @click="$emit('parent', record)"
          >查看上级记录</el-button
        >
        <el-button
          v-for="child in children"
          :key="child.code"
          @click="$emit('children', record, child.code)"
        >
          {{ child.label }}
        </el-button>
      </div>
      <dl class="record-fields">
        <div v-for="field in domain?.fields || []" :key="field.key">
          <dt>{{ field.label }}</dt>
          <dd
            >{{ legacyValueText(record.values[field.key]) }}
            <el-button
              v-if="reference(field.key)"
              link
              type="primary"
              @click="$emit('reference', reference(field.key)!.domain, record.values[field.key]!)"
              >{{ reference(field.key)!.label }}</el-button
            >
          </dd>
        </div>
      </dl>
      <el-alert
        v-if="record.redactedFields.length"
        title="本条记录的敏感内容已隐藏。"
        type="info"
        :closable="false"
      />
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  legacyChildren,
  legacyReferences,
  legacyValueText,
  legacyNativeStatusText,
  legacyNativeTargetLabel,
  type LegacyDomain,
  type LegacyRecord
} from '@/api/pms/project/legacy-detail'
const props = defineProps<{
  record?: LegacyRecord
  domain?: LegacyDomain
  domains: LegacyDomain[]
}>()
defineEmits<{
  close: []
  parent: [LegacyRecord]
  children: [LegacyRecord, string]
  reference: [string, string]
}>()
const children = computed(() =>
  props.domains.filter((d) => (legacyChildren[props.record?.domain || ''] || []).includes(d.code))
)
const reference = (field: string) => {
  const value = props.record?.values[field]
  const target = legacyReferences[props.record?.domain || '']?.[field]
  return value && value !== '0' && target && props.domains.some((d) => d.code === target.domain)
    ? target
    : undefined
}
</script>

<style scoped>
.record-origin {
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}
.related-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
.related-actions .el-button {
  margin-left: 0;
}
.record-fields {
  margin: 0 0 16px;
}
.record-fields > div {
  display: grid;
  grid-template-columns: minmax(100px, 30%) minmax(0, 1fr);
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
dt {
  color: var(--el-text-color-secondary);
}
dd {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
