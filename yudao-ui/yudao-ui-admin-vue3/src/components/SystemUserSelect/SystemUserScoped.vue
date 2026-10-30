<template>
  <div class="system-user-scoped" :aria-busy="resolvingHistory && historicalIds.length > 0" @input="inputChanged" @compositionend="inputChanged">
    <el-select
      ref="selectRef"
      :style="{ visibility: resolvingHistory && historicalIds.length ? 'hidden' : undefined }"
      :model-value="selectModel"
      :disabled="disabled"
      :clearable="clearable"
      :multiple="multiple"
      :placeholder="placeholder"
      :loading="loading && loadingPage === 1"
      filterable
      remote
      :debounce="0"
      :remote-method="scheduleSearch"
      @visible-change="open"
      @popup-scroll="popupScroll"
      @update:model-value="update"
    >
      <el-option
        v-for="id in historicalIds"
        :key="`history-${id}-${labelFor(id)}`"
        :value="id"
        :label="labelFor(id)"
        disabled
      />
      <el-option
        v-for="row in rows"
        :key="String(row.id)"
        :value="optionValue(row.id)"
        :label="labelFor(row.id)"
        :disabled="disabledIds.some((id) => sameSystemUserId(id, row.id))"
      >
        <div class="flex items-center gap-8px"><span>{{ labelFor(row.id) }}</span><el-tag v-if="row.deptName" size="small" type="info" class="ml-4px">{{ row.deptName }}</el-tag></div>
      </el-option>
      <template v-if="paginationVisible" #footer>
        <span
          class="pagination-status"
          data-testid="user-pagination-status"
          role="status"
          aria-live="polite"
          >{{ paginationStatus }}</span
        >
      </template>
    </el-select>
    <p v-if="error" role="alert"
      >{{ error
      }}<el-button link type="primary" :disabled="disabled || loading" @click="retry"
        >重试</el-button
      ></p
    >
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { ElSelect } from 'element-plus'
import {
  systemUserSelection,
  sameSystemUserId,
  type SystemUserId,
  type SystemUserSelectionUser,
  type ScopedUserSelectionSource
} from '@/api/system/user/selection'
const props = withDefaults(
  defineProps<{
    modelValue?: SystemUserId | SystemUserId[]
    scopedSource: ScopedUserSelectionSource
    disabled?: boolean
    clearable?: boolean
    multiple?: boolean
    placeholder?: string
    currentLabel?: string
    disabledIds?: SystemUserId[]
    pageSize?: number
    valueField?: 'id' | 'nickname'
    optionLabel?: 'nickname' | 'nickname-account'
  }>(),
  {
    disabled: false,
    clearable: true,
    multiple: false,
    placeholder: '请选择用户',
    currentLabel: '',
    disabledIds: () => [],
    pageSize: 20,
    valueField: 'id',
    optionLabel: 'nickname'
  }
)
const emit = defineEmits<{
  'update:modelValue': [value: SystemUserId | SystemUserId[] | undefined]
  change: [value: SystemUserSelectionUser | SystemUserSelectionUser[] | undefined]
  loaded: [rows: SystemUserSelectionUser[]]
}>()
const rows = ref<SystemUserSelectionUser[]>([]),
  history = ref<SystemUserSelectionUser[]>([]),
  total = ref(0),
  pageNo = ref(0),
  keyword = ref(''),
  loading = ref(false),
  error = ref('')
const resolvingHistory = ref(false)
const loadingPage = ref(0)
const paginationVisible = ref(false)
const paginationStatus = computed(() =>
  loading.value
    ? '正在加载'
    : error.value
      ? '加载失败，请重试'
      : rows.value.length < total.value
        ? '加载更多'
        : '已全部加载'
)
let sequence = 0,
  resolveSequence = 0
const nameSelection = ref<SystemUserSelectionUser>()
const visible = ref(false)
const selectRef = ref<InstanceType<typeof ElSelect>>()
let searchTimer: ReturnType<typeof setTimeout> | undefined
let failedLoad: { page: number; append: boolean } | undefined
let pendingQuery: string | undefined
const pendingRequests = new Map<string, Promise<{ list: SystemUserSelectionUser[]; total: number }>>()
const nameLegacyKey = computed(() => `__historical_name__${String(props.modelValue || '')}`)
const ids = computed(() =>
  props.valueField === 'nickname'
    ? []
    : Array.isArray(props.modelValue)
      ? props.modelValue
      : props.modelValue == null || props.modelValue === ''
        ? []
        : [props.modelValue]
)
const selectModel = computed(() => {
  if (props.valueField !== 'nickname') return props.modelValue
  if (props.modelValue == null || props.modelValue === '') return undefined
  return nameSelection.value?.nickname === props.modelValue
    ? nameSelection.value.id
    : nameLegacyKey.value
})
const historicalIds = computed(() => {
  const selected =
    props.valueField === 'nickname'
      ? selectModel.value == null
        ? []
        : [selectModel.value as SystemUserId]
      : ids.value
  return selected.filter((id) => !rows.value.some((row) => sameSystemUserId(row.id, id)))
})
// Long IDs may arrive as strings while a small directory ID is numeric. ElSelect compares
// option values strictly, so retain the selected value's exact type for the matching option.
const optionValue = (id: SystemUserId) => props.valueField === 'nickname'
  ? id : ids.value.find(current => sameSystemUserId(current, id)) ?? id
const labelFor = (id: SystemUserId) => {
  if (props.valueField === 'nickname' && id === nameLegacyKey.value)
    return String(props.modelValue || '')
  const row =
    rows.value.find((row) => sameSystemUserId(row.id, id)) ||
    history.value.find((row) => sameSystemUserId(row.id, id)) ||
    (sameSystemUserId(nameSelection.value?.id, id) ? nameSelection.value : undefined)
  if (row && props.optionLabel === 'nickname-account' && row.nickname && row.username)
    return `${row.nickname}（${row.username}）`
  return row?.nickname || row?.username || props.currentLabel || `用户 ${id}（当前不可选）`
}
function clearCandidatePage() {
  const selectedIds =
    props.valueField === 'nickname'
      ? nameSelection.value ? [nameSelection.value.id] : []
      : ids.value
  const selected = rows.value.filter((row) =>
    selectedIds.some((id) => sameSystemUserId(id, row.id))
  )
  history.value = [...new Map([...history.value, ...selected].map((row) => [String(row.id), row])).values()]
  rows.value = []
  total.value = 0
  pageNo.value = 0
  paginationVisible.value = false
}
function invalidateSearch(value: string) {
  keyword.value = value
  sequence++
  clearCandidatePage()
  pendingQuery = undefined
  failedLoad = undefined
  error.value = ''
  loading.value = true
  loadingPage.value = 1
}
function scheduleSearch(value: string) {
  if (props.disabled || (value === keyword.value && pageNo.value > 0 && !error.value)) return
  if (value !== keyword.value) invalidateSearch(value)
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchTimer = undefined
    search(value)
  }, 300)
}
function inputChanged(event: Event) {
  const target = event.target as HTMLInputElement | null
  if (props.disabled || target?.tagName !== 'INPUT' || typeof target.value !== 'string') return
  if (target.value === keyword.value && event.type !== 'compositionend') return
  // Let ElSelect read the native value first, then invalidate before its remote debounce.
  // Own the timer even when ElSelect suppresses a repeated query.
  if (target.value !== keyword.value) {
    clearTimeout(searchTimer)
    invalidateSearch(target.value)
  }
  if (!(event as InputEvent).isComposing) scheduleSearch(target.value)
}
async function load(page: number, append = false) {
  if (props.disabled) return
  const queryKey = JSON.stringify([props.scopedSource.key, keyword.value, page, props.pageSize])
  if (loading.value && pendingQuery === queryKey) return
  const request = ++sequence,
    key = props.scopedSource.key
  if (!append) clearCandidatePage()
  pendingQuery = queryKey
  loading.value = true
  loadingPage.value = page
  error.value = ''
  failedLoad = undefined
  let operation = pendingRequests.get(queryKey)
  try {
    if (!operation) {
      operation = systemUserSelection.scoped(props.scopedSource, {
        keyword: keyword.value,
        pageNo: page,
        pageSize: props.pageSize
      })
      pendingRequests.set(queryKey, operation)
    }
    const data = await operation
    if (request !== sequence || key !== props.scopedSource.key) return
    rows.value = append
      ? [...new Map([...rows.value, ...data.list].map((row) => [String(row.id), row])).values()]
      : data.list
    total.value = data.total
    if (!append) paginationVisible.value = data.total > rows.value.length
    pageNo.value = page
    emit('loaded', rows.value)
  } catch {
    if (request === sequence) {
      error.value = '人员候选加载失败，已选编号保留。'
      failedLoad = { page, append }
      if (!append) {
        rows.value = []
        total.value = 0
      }
    }
  } finally {
    if (pendingRequests.get(queryKey) === operation) pendingRequests.delete(queryKey)
    if (request === sequence) {
      loading.value = false
      pendingQuery = undefined
    }
  }
}
function search(value: string) {
  clearTimeout(searchTimer)
  searchTimer = undefined
  if (value === keyword.value && pageNo.value > 0 && !error.value) return
  keyword.value = value
  void load(1)
}
function open(value: boolean) {
  visible.value = value
  if (value && pageNo.value === 0) void load(1)
}
function retry() {
  void resolveHistory()
  if (props.disabled || loading.value) return
  const failed = failedLoad
  void load(failed?.page || 1, failed?.append || false)
}
function more() {
  if (props.disabled || loading.value || error.value || rows.value.length >= total.value) return
  void load(pageNo.value + 1, true)
}
function popupScroll({ scrollTop }: { scrollTop: number }) {
  const wrap = selectRef.value?.scrollbarRef?.wrapRef
  if (!visible.value || pageNo.value === 0 || !wrap) return
  if (wrap.scrollHeight - wrap.clientHeight - scrollTop <= 24) more()
}
function update(value: SystemUserId | SystemUserId[] | undefined) {
  if (props.disabled) return
  if (props.valueField === 'nickname') {
    const user = rows.value.find((row) => sameSystemUserId(row.id, value))
    if (value != null && value !== '' && !user) return
    nameSelection.value = user
    emit('update:modelValue', user?.nickname)
    emit('change', user)
    return
  }
  const selected = Array.isArray(value) ? value : value == null ? [] : [value]
  if (selected.some((id) => !ids.value.some((current) => sameSystemUserId(current, id))
      && !rows.value.some((row) => sameSystemUserId(row.id, id)))) return
  emit('update:modelValue', value)
  const users = rows.value.filter((row) => selected.some((id) => sameSystemUserId(id, row.id)))
  emit('change', props.multiple ? users : users[0])
}
watch(
  () => props.scopedSource.key,
  () => {
    clearTimeout(searchTimer)
    searchTimer = undefined
    sequence++
    resolveSequence++
    nameSelection.value = undefined
    rows.value = []
    history.value = []
    pageNo.value = 0
    total.value = 0
    keyword.value = ''
    error.value = ''
    failedLoad = undefined
    pendingQuery = undefined
    pendingRequests.clear()
    paginationVisible.value = false
    loading.value = false
    if (visible.value && !props.disabled) void load(1)
  }
)
async function resolveHistory() {
  const request = ++resolveSequence,
    key = props.scopedSource.key
  if (!ids.value.length) {
    history.value = []
    resolvingHistory.value = false
    return
  }
  resolvingHistory.value = true
  try {
    const users = await systemUserSelection.scopedResolve(props.scopedSource, ids.value)
    if (request === resolveSequence && key === props.scopedSource.key) history.value = users
  } catch {
    if (request === resolveSequence) error.value = '已选人员回显失败，原编号保留。'
  } finally {
    if (request === resolveSequence) resolvingHistory.value = false
  }
}
watch(() => [props.scopedSource.key, props.modelValue], resolveHistory, {
  immediate: true,
  deep: true
})
onBeforeUnmount(() => {
  clearTimeout(searchTimer)
  sequence++
  resolveSequence++
  pendingRequests.clear()
})
</script>
<style scoped>
.system-user-scoped {
  width: 100%;
  min-width: 0;
}

.system-user-scoped :deep(.el-select) {
  width: 100%;
}

.system-user-scoped p {
  margin: 6px 0;
  color: var(--el-color-danger);
}

.pagination-status {
  display: block;
  height: 24px;
  overflow: hidden;
  line-height: 24px;
  color: var(--el-text-color-secondary);
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
