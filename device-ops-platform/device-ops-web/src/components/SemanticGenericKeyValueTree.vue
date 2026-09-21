<script setup lang="ts">
import type { GenericKeyValueEntry } from '@/types/parser'

defineOptions({ name: 'SemanticGenericKeyValueTree' })
withDefaults(defineProps<{ entries: GenericKeyValueEntry[]; depth?: number }>(), { depth: 0 })
</script>

<template>
  <pre
    v-if="depth >= 8"
    class="generic-kv__fallback"
  >{{ JSON.stringify(entries, null, 2) }}</pre>
  <ul
    v-else
    class="generic-kv"
    role="list"
  >
    <li
      v-for="(entry, index) in entries"
      :key="`${entry.startLine}-${entry.key}-${index}`"
    >
      <div class="generic-kv__row">
        <strong>{{ entry.key }}</strong>
        <span v-if="entry.value">{{ entry.value }}</span>
        <small>行 {{ entry.startLine }}{{ entry.endLine !== entry.startLine ? `–${entry.endLine}` : '' }}</small>
      </div>
      <SemanticGenericKeyValueTree
        v-if="entry.children.length"
        :entries="entry.children"
        :depth="depth + 1"
      />
    </li>
  </ul>
</template>

<style scoped>
.generic-kv { display: grid; gap: 0.375rem; margin: 0; padding-left: 1rem; list-style: none; border-left: 1px solid var(--line); }
.generic-kv__row { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: baseline; min-width: 0; }
.generic-kv__row strong { color: var(--ink); }
.generic-kv__row span { overflow-wrap: anywhere; white-space: pre-wrap; }
.generic-kv__row small { margin-left: auto; color: var(--slate); }
.generic-kv__fallback { max-height: 16rem; overflow: auto; margin: 0; font-size: 0.75rem; }
</style>
