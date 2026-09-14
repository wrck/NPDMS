<template>
  <section aria-label="审批流程版本选择">
    <p v-if="binding?.type === 'APPROVAL'" class="approval-pin">
      当前冻结：{{ binding.approvalDefinitionKey }} · {{ pinnedId || '缺少精确版本，请重新选择' }}
    </p>
    <template v-if="!readonly">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-form label-position="top" @submit.prevent="search">
        <el-form-item label="流程标识（可选，精确查询全部版本）">
          <el-input v-model="queryKey" clearable :disabled="busy" @keyup.enter="search" />
        </el-form-item>
        <el-button :loading="loading" :disabled="busy || !canBind" @click="search">查询审批流程</el-button>
        <el-form-item label="已部署的审批流程版本">
          <el-select aria-label="审批流程精确版本" :model-value="pinnedId" filterable
            :loading="loading || busy" :disabled="loading || busy || !canBind" placeholder="选择流程名称及具体版本"
            @update:model-value="choose">
            <el-option v-for="row in rows" :key="row.id" :value="row.id" :label="label(row)"
              :disabled="row.suspensionState !== 1 || row.formType !== BpmModelFormType.NORMAL" />
            <el-option v-if="pinnedId && !rows.some(row => row.id === pinnedId)" :value="pinnedId"
              :label="`${binding?.approvalDefinitionKey} · ${pinnedId}（当前冻结）`" disabled />
          </el-select>
        </el-form-item>
        <el-button v-if="rows.length < total" :loading="loading" :disabled="busy" link @click="loadMore">加载更多流程版本</el-button>
      </el-form>
      <p class="help">选择只修改当前草稿；保存不发起审批。发布时复验精确版本，不自动跟随同名流程更新。</p>
      <p class="help">停用流程不可选；原模块业务表单通过业务页面绑定办理。查看流程目录沿用 BPM 查询权限。</p>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getProcessDefinition, getProcessDefinitionPage } from '@/api/bpm/definition'
import { BpmModelFormType } from '@/utils/constants'
import { hasPermission } from '@/directives/permission/hasPermi'
import type { WorkBindingSpec } from '@/api/pms/project/project-templates'

export interface ApprovalDefinitionChoice {
  id: string
  key: string
  name: string
  version: number
}
interface DefinitionRow extends ApprovalDefinitionChoice { suspensionState: number; formType?: number }
const props = defineProps<{ binding?: WorkBindingSpec; readonly?: boolean;
  bindingPermission?: 'pms:project-template:update' | 'pms:project-plan:manage' }>()
const emit = defineEmits<{ choose: [definition: ApprovalDefinitionChoice] }>()
const canBind = computed(() => hasPermission([props.bindingPermission ?? 'pms:project-template:update']))
const pinnedId = computed(() => props.binding?.type === 'APPROVAL' && typeof props.binding.parameters?.processDefinitionId === 'string'
  ? props.binding.parameters.processDefinitionId : undefined)
const rows = ref<DefinitionRow[]>([]), total = ref(0), queryKey = ref(''), loading = ref(false), busy = ref(false), error = ref('')
let pageNo = 0, activeKey = '', request = 0, disposed = false
const label = (row: DefinitionRow) => `${row.name} · ${row.key} · 第${row.version}版${row.suspensionState !== 1 ? '（已停用）' : row.formType !== BpmModelFormType.NORMAL ? '（原模块业务表单）' : ''}`
const loadMore = async () => {
  if (loading.value || props.readonly || !canBind.value) return
  const token = ++request, next = pageNo + 1
  loading.value = true; error.value = ''
  try {
    const result = await getProcessDefinitionPage({ pageNo: next, pageSize: 20, key: activeKey || undefined })
    if (disposed || token !== request) return
    rows.value.push(...result.list); total.value = result.total; pageNo = next
  } catch { if (!disposed && token === request) error.value = '流程目录读取失败，请检查 BPM 查询权限后重试；当前冻结配置未改变。' }
  finally { if (!disposed && token === request) loading.value = false }
}
const search = async () => {
  if (props.readonly || !canBind.value || busy.value) return
  ++request; loading.value = false; pageNo = 0; activeKey = queryKey.value.trim(); rows.value = []; total.value = 0
  await loadMore()
}
const choose = async (id: string) => {
  if (busy.value || loading.value || props.readonly || !canBind.value) return
  const row = rows.value.find(row => row.id === id)
  if (!row || row.suspensionState !== 1 || row.formType !== BpmModelFormType.NORMAL) return
  const token = ++request
  busy.value = true; error.value = ''
  try {
    const actual = await getProcessDefinition(id)
    if (disposed || token !== request || props.readonly || !canBind.value) return
    if (actual?.id !== id || actual.key !== row.key || actual.version !== row.version || actual.suspensionState !== 1
      || actual.formType !== BpmModelFormType.NORMAL) throw new Error('Definition changed')
    emit('choose', { id, key: actual.key, name: actual.name, version: actual.version })
  } catch { if (!disposed && token === request) error.value = '所选流程版本已不可用，请重新查询；当前绑定保持不变。' }
  finally { if (!disposed && token === request) busy.value = false }
}
onMounted(() => { if (!props.readonly) void search() })
onBeforeUnmount(() => { disposed = true; ++request })
</script>
<style scoped>
.approval-pin { overflow-wrap: anywhere; }
.help { margin: 8px 0; color: var(--el-text-color-secondary); font-size: 12px; }
section :deep(.el-select) { width: 100%; }
</style>
