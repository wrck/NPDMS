<template>
  <div class="definition-select">
    <el-select :model-value="modelValue" filterable clearable :disabled="disabled" :loading="loading" :placeholder="`选择已发布${definitionKinds[kind]}修订`" @update:model-value="select" @visible-change="(visible) => visible && load()">
      <el-option v-for="row in rows" :key="row.id" :value="row.id" :label="business ? `${row.payload.name ?? row.payload.deliverableType ?? row.definitionCode} · 第${row.revisionNo}版` : `${row.definitionCode} · r${row.revisionNo} · #${row.id}`" />
      <el-option v-if="modelValue && !rows.some((row) => row.id === modelValue)" :value="modelValue" :label="`${selectedLabel}（仅保留引用，发布时重验）`" disabled />
    </el-select>
    <div v-if="failure" class="text-danger text-12px" role="alert">{{ failure }} <el-button link @click="load">重试</el-button></div>
    <el-collapse v-if="selected && !business" class="mt-4px">
      <el-collapse-item :title="`引用详情：${selected.definitionCode} r${selected.revisionNo}${selected.disabledAt ? ' · 已停用' : ''}`">
        <pre class="definition-preview">{{ JSON.stringify(selected.payload, null, 2) }}</pre>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { availableDefinition, definitionKinds, getDefinition, getDefinitionPage, type DefinitionKind, type DefinitionRevision } from '@/api/pms/project/project-templates/definitions'
import { errorText } from './editorModel'
const props = defineProps<{ modelValue?: number; kind: DefinitionKind; disabled?: boolean; business?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: number | undefined]; selected: [value: DefinitionRevision | undefined] }>()
const rows = ref<DefinitionRevision[]>([])
const selected = ref<DefinitionRevision>()
const selectedLabel = ref('')
const loading = ref(false)
const failure = ref('')
let generation = 0
const load = async () => {
  const current = ++generation
  loading.value = true
  failure.value = ''
  try {
    const list: DefinitionRevision[] = []
    let pageNo = 1
    let total = 0
    do {
      const page = await getDefinitionPage({ pageNo: pageNo++, pageSize: 100, definitionKind: props.kind, revisionState: 'PUBLISHED' })
      list.push(...page.list)
      total = page.total
      if (!page.list.length) break
    } while (list.length < total)
    if (current === generation) rows.value = list.filter((row) => availableDefinition(row, props.kind))
  } catch (error) { if (current === generation) failure.value = errorText(error) }
  finally { if (current === generation) loading.value = false }
}
const select = (id: number | '') => {
  const value = id || undefined
  emit('update:modelValue', value)
  emit('selected', rows.value.find((row) => row.id === value))
}
watch(() => props.kind, load, { immediate: true })
watch(() => props.modelValue, async (id) => {
  selected.value = undefined
  selectedLabel.value = `修订 #${id}`
  if (!id) return
  try {
    const row = await getDefinition(id)
    if (props.modelValue !== id) return
    selected.value = row
    selectedLabel.value = `${row.definitionCode} r${row.revisionNo}`
  } catch { /* Preserve missing/inaccessible historical IDs without inventing a replacement. */ }
}, { immediate: true })
</script>
<style scoped>
.definition-select { min-width: 210px; }
.definition-select :deep(.el-select) { width: 100%; }
.definition-preview { white-space: pre-wrap; overflow-wrap: anywhere; max-height: 240px; overflow: auto; margin: 0; }
</style>
