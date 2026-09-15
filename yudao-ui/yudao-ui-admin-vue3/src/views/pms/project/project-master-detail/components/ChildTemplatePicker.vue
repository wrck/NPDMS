<template>
  <div class="child-template-picker">
    <span>{{ selected && selected.revisionId === revisionId ? `${selected.name} · v${selected.revisionNo}` : (revisionId ? `已选模板版本 #${revisionId}` : '尚未选择模板') }}</span>
    <el-button :disabled="disabled" @click="open">选择模板</el-button>
    <el-dialog v-model="visible" title="子项目独立选择模板" width="min(640px, 95vw)" append-to-body>
      <el-input v-model="name" aria-label="按模板名称搜索" placeholder="按模板名称搜索" clearable @keyup.enter="search" />
      <el-button :loading="loading" @click="search">查询</el-button>
      <el-alert v-if="failed" title="模板列表读取失败，请重试" type="error" :closable="false" />
      <div v-loading="loading" class="template-options">
        <el-button v-for="option in options" :key="option.templateId" :disabled="!option.selectable || loading || disabled" @click="choose(option)">
          {{ option.name }} · {{ option.revisionNo ? `v${option.revisionNo}` : '未发布' }}
          {{ !option.selectable ? '（未发布、不可用或缺少权限）' : option.recommended ? '（匹配推荐）' : '（需要覆盖权限及原因）' }}
        </el-button>
        <el-empty v-if="!loading && !failed && !options.length" description="没有可用的模板选项" />
      </div>
      <el-pagination v-model:current-page="pageNo" :page-size="20" :total="total" layout="prev, pager, next" @current-change="changePage" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { getChildTemplateOptions, type ChildTemplateOption, type LongId } from '@/api/pms/project/project-splits'

const props = defineProps<{ parentProjectId: number; projectName?: string; businessLevelCode?: string; officeDepartmentCode?: string; revisionId?: LongId; disabled?: boolean }>()
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

const load = async () => {
  const current = ++generation
  loading.value = true; failed.value = false; options.value = []
  try {
    const result = await getChildTemplateOptions({ parentProjectId: props.parentProjectId, projectName: props.projectName, businessLevelCode: props.businessLevelCode, officeDepartmentCode: props.officeDepartmentCode, pageNo: pageNo.value, pageSize: 20, name: name.value.trim() || undefined })
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
watch(() => [props.parentProjectId, props.projectName, props.businessLevelCode, props.officeDepartmentCode], () => { ++generation; visible.value = false; options.value = []; selected.value = undefined; loading.value = false }, { flush: 'sync' })
onBeforeUnmount(() => { ++generation })
</script>

<style scoped>
.child-template-picker, .template-options { display: flex; gap: 8px; flex-wrap: wrap; }
.template-options { margin: 16px 0; flex-direction: column; align-items: stretch; }
.template-options :deep(.el-button) { margin-left: 0; height: auto; min-height: 32px; }
.template-options :deep(.el-button > span) { white-space: normal; }
</style>
