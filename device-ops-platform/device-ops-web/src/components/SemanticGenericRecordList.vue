<script setup lang="ts">
import { ref } from 'vue'
import SemanticGenericKeyValueTree from '@/components/SemanticGenericKeyValueTree.vue'
import SemanticGenericSection from '@/components/SemanticGenericSection.vue'
import type { GenericRecordListSection } from '@/types/parser'
defineProps<{ section: GenericRecordListSection }>()
const active = ref<string[]>([])
</script>

<template>
  <el-collapse v-model="active">
    <el-collapse-item
      v-for="record in section.records"
      :key="record.recordIndex"
      :name="String(record.recordIndex)"
      :title="record.identity || `#${record.recordIndex}`"
    >
      <template v-if="active.includes(String(record.recordIndex))">
        <template v-if="record.sections?.length">
          <SemanticGenericSection
            v-for="childSection in record.sections"
            :key="`${record.recordIndex}:${childSection.sectionIndex}`"
            :section="childSection"
          />
        </template>
        <SemanticGenericKeyValueTree
          v-else
          :entries="record.entries"
        />
      </template>
    </el-collapse-item>
  </el-collapse>
</template>
