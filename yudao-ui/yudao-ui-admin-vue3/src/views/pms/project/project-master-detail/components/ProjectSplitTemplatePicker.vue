<template>
  <div class="child-template-picker">
    <div class="template-selection" :class="{ selected: !!revisionId }">
      <div><strong>{{ selected && String(selected.revisionId) === String(revisionId) ? selected.name : revisionId ? '已选择发布模板' : '选择子项目模板' }}</strong>
        <span>{{ selected && String(selected.revisionId) === String(revisionId) ? `发布版本 v${selected.revisionNo}` : revisionId ? '已保存，可打开列表查看或更换' : '独立配置该子项目的阶段与交付流程' }}</span></div>
      <el-button :disabled="disabled" @click="open">{{ revisionId ? '更换模板' : '选择模板' }}</el-button>
    </div>
    <el-dialog v-model="visible" title="子项目独立选择模板" width="min(640px, 95vw)" append-to-body>
      <div class="template-search"><el-input v-model="name" aria-label="按模板名称搜索" placeholder="按模板名称搜索" clearable @keyup.enter="search" />
        <el-button :loading="loading" @click="search">查询</el-button></div>
      <el-alert v-if="failed" title="模板列表读取失败，请重试" type="error" :closable="false" />
      <div v-loading="loading" class="template-options">
        <el-button v-for="option in options" :key="option.templateId" :class="{ 'current-template': String(option.revisionId) === String(revisionId) }" :disabled="!option.selectable || loading || disabled" @click="choose(option)">
          <div class="template-option-name"><strong>{{ option.name }}</strong><span>{{ option.revisionNo ? `v${option.revisionNo}` : '未发布' }}</span></div>
          <span class="template-option-note" :class="{ recommended: option.recommended }">{{ !option.selectable ? '未发布、不可用或缺少权限' : option.recommended ? '匹配推荐' : '需覆盖权限并填写选择原因' }}{{ String(option.revisionId) === String(revisionId) ? ' / 当前已选' : '' }}</span>
        </el-button>
        <el-empty v-if="!loading && !failed && !options.length" description="没有可用的模板选项" />
      </div>
      <el-pagination v-model:current-page="pageNo" :page-size="20" :total="total" layout="prev, pager, next" @current-change="changePage" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { getChildTemplateOptions, type ChildTemplateOption, type LongId } from '@/api/pms/project/project-splits/workspace'

const props = defineProps<{ parentProjectId: number; projectName?: string; businessLevelCode?: string; departmentCode?: string; revisionId?: LongId; disabled?: boolean }>()
const emit = defineEmits<{ change: [revisionId: LongId] }>()
const visible = ref(false)
const name = ref('')
const pageNo = ref(1)
const total = ref(0)
const options = ref<ChildTemplateOption[]>([])
const selected = ref<ChildTemplateOption>()
const loading = ref(false)
const failed = ref(false)
let generation = 0
let selectionGeneration = 0

const load = async () => {
  const current = ++generation
  loading.value = true; failed.value = false; options.value = []
  try {
    const result = await getChildTemplateOptions({ parentProjectId: props.parentProjectId, projectName: props.projectName, businessLevelCode: props.businessLevelCode, departmentCode: props.departmentCode || undefined, pageNo: pageNo.value, pageSize: 20, name: name.value.trim() || undefined })
    if (current !== generation) return
    options.value = result.list; total.value = result.total
  } catch {
    if (current === generation) failed.value = true
  } finally { if (current === generation) loading.value = false }
}
const open = () => { if (props.disabled) return; visible.value = true; pageNo.value = 1; void load() }
const search = () => { pageNo.value = 1; void load() }
const changePage = (page: number) => { pageNo.value = page; void load() }
const choose = (option: ChildTemplateOption) => {
  if (props.disabled || loading.value || !option.selectable || !option.revisionId) return
  selected.value = option
  emit('change', option.revisionId); visible.value = false
}
watch(() => [props.parentProjectId, props.projectName, props.businessLevelCode, props.departmentCode], () => { ++generation; visible.value = false; options.value = []; loading.value = false }, { flush: 'sync' })
// A restored draft contains the exact revision, so resolve its label without changing the selection.
watch(() => [props.parentProjectId, props.revisionId] as const, async ([parentProjectId, revisionId]) => {
  const current = ++selectionGeneration
  if (revisionId && String(selected.value?.revisionId) === String(revisionId)) return
  selected.value = undefined
  if (!revisionId) return
  const context = { parentProjectId, projectName: props.projectName, businessLevelCode: props.businessLevelCode, departmentCode: props.departmentCode || undefined }
  try {
    for (let page = 1; ; page++) {
      const result = await getChildTemplateOptions({ ...context, pageNo: page, pageSize: 20 })
      if (current !== selectionGeneration) return
      const match = result.list.find(option => String(option.revisionId) === String(revisionId))
      if (match) { selected.value = match; return }
      if (!result.list.length || page * 20 >= result.total) return
    }
  } catch { /* Keep the saved revision and allow the user to retry from the picker. */ }
}, { immediate: true })
onBeforeUnmount(() => { ++generation; ++selectionGeneration })
</script>

<style scoped>
.child-template-picker { width: 100%; min-width: 0; }
.template-selection { display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%; padding: 12px; border: 1px dashed var(--el-border-color); border-radius: 4px; background: var(--el-fill-color-lighter); }
.template-selection.selected { border-style: solid; border-color: var(--el-color-primary-light-7); background: var(--el-color-primary-light-9); }
.template-selection > div { min-width: 0; }
.template-selection strong { display: block; font-size: 13px; line-height: 1.6; font-weight: 500; color: var(--el-text-color-primary); overflow-wrap: anywhere; }
.template-selection > div > span { display: block; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; margin-top: 3px; }
.template-selection :deep(.el-button) { flex: none; }
.template-search { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.template-options { display: grid; gap: 10px; margin: 16px 0; }
.template-options :deep(.el-button) { margin-left: 0; padding: 14px 16px; height: auto; justify-content: flex-start; text-align: left; }
.template-options :deep(.el-button > span) { display: block; width: 100%; white-space: normal; }
.template-option-name { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; line-height: 1.5; }
.template-option-name strong { font-weight: 500; }
.template-option-name > span { color: var(--el-text-color-secondary); font-size: 12px; flex: none; }
.template-option-note { display: block; margin-top: 8px; font-size: 12px; color: var(--el-text-color-secondary); }
.template-option-note.recommended { color: var(--el-color-primary); }
.template-options .current-template { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
</style>
