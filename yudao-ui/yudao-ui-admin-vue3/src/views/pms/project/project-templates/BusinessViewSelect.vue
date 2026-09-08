<template>
  <div>
    <div class="view-select">
      <el-select v-model="source" :disabled="disabled" placeholder="全部来源" clearable><el-option value="PAGE" label="已注册页面 PAGE" /><el-option value="DYNAMIC_FORM" label="动态表单 DYNAMIC_FORM" /></el-select>
      <el-select :model-value="selectionValue" filterable clearable :disabled="disabled" :loading="loading" placeholder="选择已发布、未停用的精确视图" @update:model-value="select" @visible-change="(visible) => visible && load()">
        <el-option v-for="row in choices" :key="row.id" :value="row.id" :label="`${row.viewKey} r${row.revisionNo} · ${row.viewSource} · ${row.entityType}`" />
        <el-option v-if="modelValue && !choices.some((row) => sameBusinessViewId(row.id, modelValue))" :value="modelValue" :label="`保留修订 #${modelValue}（不在当前可选列表，发布时重验）`" disabled />
      </el-select>
    </div>
    <div v-if="failure" role="alert">{{ failure }} <el-button link @click="load">重新加载视图</el-button></div>
    <el-descriptions v-if="selected" :column="2" border class="mt-8px">
      <el-descriptions-item label="Owner">{{ selected.ownerContext }}</el-descriptions-item>
      <el-descriptions-item label="实体">{{ selected.entityType }}</el-descriptions-item>
      <el-descriptions-item label="组件">{{ selected.componentKey }} @{{ selected.componentVersion }}</el-descriptions-item>
      <el-descriptions-item label="表单修订">{{ selected.dynamicFormRevisionId ?? '不适用' }}</el-descriptions-item>
      <el-descriptions-item label="上下文契约" :span="2"><pre>{{ JSON.stringify(selected.contextSchema, null, 2) }}</pre></el-descriptions-item>
    </el-descriptions>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { getBusinessView, getBusinessViewPage, type BusinessViewId, type BusinessViewRegistrationVO, type BusinessViewSource } from '@/api/pms/platform/business-view'
import { sameBusinessViewId } from '@/api/pms/platform/business-view/ids'
import { errorText } from './editorModel'
const props = defineProps<{ modelValue?: BusinessViewId; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: BusinessViewId | undefined]; selected: [value: BusinessViewRegistrationVO | undefined] }>()
const source = ref<BusinessViewSource | ''>('')
const rows = ref<BusinessViewRegistrationVO[]>([])
const selected = ref<BusinessViewRegistrationVO>()
const loading = ref(false)
const failure = ref('')
const choices = computed(() => rows.value.filter((row) => row.status === 'PUBLISHED' && !row.disabledAt && (!source.value || row.viewSource === source.value)))
// Element Plus matches option values strictly. Use the exact wire value without rewriting the model.
const selectionValue = computed(() => choices.value.find((row) => sameBusinessViewId(row.id, props.modelValue))?.id ?? props.modelValue)
const load = async () => {
  loading.value = true
  failure.value = ''
  try {
    const list: BusinessViewRegistrationVO[] = []
    let pageNo = 1
    let total = 0
    do {
      const page = await getBusinessViewPage({ pageNo: pageNo++, pageSize: 100 })
      list.push(...page.list)
      total = page.total
      if (!page.list.length) break
    } while (list.length < total)
    rows.value = list
  } catch (error) { failure.value = errorText(error) }
  finally { loading.value = false }
}
const select = (id: BusinessViewId | undefined) => {
  const row = choices.value.find((row) => sameBusinessViewId(row.id, id))
  emit('update:modelValue', row?.id ?? (id === '' ? undefined : id))
  emit('selected', row)
}
watch(() => props.modelValue, async (id) => {
  selected.value = undefined
  if (!id) return
  try {
    const row = await getBusinessView(id)
    if (sameBusinessViewId(props.modelValue, id)) selected.value = row
  } catch { /* Historical IDs remain visible even when the query is denied/unavailable. */ }
}, { immediate: true })
onMounted(load)
</script>
<style scoped>
.view-select { display: flex; gap: 8px; flex-wrap: wrap; }
.view-select :deep(.el-select) { min-width: 240px; flex: 1; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; margin: 0; }
</style>
