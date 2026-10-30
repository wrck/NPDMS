<script setup lang="ts">
import { ref } from 'vue'
import type { GenericConfigSection } from '@/types/parser'
defineProps<{ section: GenericConfigSection }>()
const active = ref<string[]>([])
</script>

<template>
  <el-collapse v-model="active">
    <el-collapse-item
      v-for="(stanza, index) in section.stanzas"
      :key="`${stanza.startLine}-${index}`"
      :name="String(index)"
      :title="stanza.header"
    >
      <pre
        v-if="active.includes(String(index))"
        class="generic-config__lines"
      >{{ stanza.lines.join('\n') }}</pre>
    </el-collapse-item>
  </el-collapse>
</template>

<style scoped>
.generic-config__lines { max-height: 20rem; overflow: auto; margin: 0; font: 0.75rem/1.55 "Cascadia Code", monospace; white-space: pre; }
</style>
