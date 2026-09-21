<script setup lang="ts">
import SemanticGenericConfig from '@/components/SemanticGenericConfig.vue'
import SemanticGenericKeyValueTree from '@/components/SemanticGenericKeyValueTree.vue'
import SemanticGenericRecordList from '@/components/SemanticGenericRecordList.vue'
import SemanticGenericTable from '@/components/SemanticGenericTable.vue'
import type { GenericConfigSection, GenericKeyValueSection, GenericListSection,
  GenericRecordListSection, GenericSection, GenericTableSection, GenericTextSection } from '@/types/parser'
defineProps<{ section: GenericSection }>()
</script>

<template>
  <article class="generic-section">
    <div
      v-if="section.warnings.length"
      class="generic-section__warnings"
      role="status"
    >
      <el-tag
        v-for="warning in section.warnings"
        :key="`${warning.lineNumber}-${warning.code}`"
        type="warning"
        size="small"
      >
        {{ warning.code }} · 行 {{ warning.lineNumber }}
      </el-tag>
    </div>
    <SemanticGenericKeyValueTree
      v-if="section.type === 'keyValue' || section.type === 'keyValueTree'"
      :entries="(section as GenericKeyValueSection).entries"
    />
    <SemanticGenericTable
      v-else-if="section.type === 'table'"
      :section="section as GenericTableSection"
    />
    <SemanticGenericRecordList
      v-else-if="section.type === 'recordList'"
      :section="section as GenericRecordListSection"
    />
    <SemanticGenericConfig
      v-else-if="section.type === 'configStanza'"
      :section="section as GenericConfigSection"
    />
    <ol
      v-else-if="section.type === 'list'"
      class="generic-section__lines"
    >
      <li
        v-for="item in (section as GenericListSection).items"
        :key="item.itemIndex"
      >
        {{ item.value }}
      </li>
    </ol>
    <pre
      v-else-if="section.type === 'text'"
      class="generic-section__text"
    >{{ (section as GenericTextSection).lines.map(line => line.value).join('\n') }}</pre>
    <el-alert
      v-else
      :title="`不支持的结构类型：${section.type}`"
      type="warning"
      :closable="false"
    />
  </article>
</template>

<style scoped>
.generic-section { min-width: 0; }
.generic-section__warnings { display: flex; flex-wrap: wrap; gap: 0.375rem; margin-bottom: 0.5rem; }
.generic-section__lines { display: grid; gap: 0.25rem; margin: 0; padding-left: 1.5rem; }
.generic-section__text { overflow: auto; margin: 0; padding: 0.5rem; background: var(--el-fill-color-light); font-size: 0.75rem; white-space: pre-wrap; }
</style>
