<!-- 系统启用用户选择：候选只代表身份，业务分派资格由业务服务校验。 -->
<template>
  <div class="user-select" :data-historical-selection="missingSelection">
    <el-select
      v-model="selectValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :clearable="clearable"
      :loading="loading"
      filterable
      :multiple="multiple"
      :filter-method="handleFilter"
      class="!w-1/1"
      @visible-change="open"
      @change="handleChange"
    >
      <slot name="prefix-options"></slot>
      <el-option
        v-for="id in missingSelections"
        :key="`history-${id}`"
        :value="id"
        :label="
          currentLabel ||
          allList.find((item) => sameId(item.id, id))?.nickname ||
          `用户 ${id}（当前不可选）`
        "
        disabled
      />
      <el-option
        v-for="item in filteredList"
        :key="String(item.id)"
        :label="item.nickname || item.username || `用户 ${item.id}`"
        :value="item.id"
        :disabled="disabledIds?.some((id) => String(id) === String(item.id))"
      >
        <div class="flex items-center gap-8px"
          ><span>{{ item.nickname || item.username || `用户 ${item.id}` }}</span
          ><el-tag v-if="item.deptName" size="small" type="info" class="ml-4px">{{
            item.deptName
          }}</el-tag></div
        >
      </el-option>
    </el-select>
    <p v-if="error" role="alert" class="user-select-error"
      >{{ error
      }}<el-button link type="primary" :disabled="disabled || loading" @click="load"
        >重新加载用户</el-button
      ></p
    >
  </div>
</template>
<script setup lang="ts" generic="T extends string | number = string | number">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type * as UserApi from '@/api/system/user'
import { systemUserSelection } from '@/api/system/user/selection'
defineOptions({ name: 'SystemUserDropdown' })
type Candidate = Omit<UserApi.UserVO, 'id'> & { id: string | number }
const props = withDefaults(
  defineProps<{
    modelValue?: T | T[]
    disabled?: boolean
    clearable?: boolean
    placeholder?: string
    currentLabel?: string
    contextKey?: string
    preserveEmptyValue?: boolean
    multiple?: boolean
    disabledIds?: (string | number)[]
    deptId?: string | number
  }>(),
  {
    disabled: false,
    clearable: true,
    placeholder: '请选择用户',
    currentLabel: '',
    contextKey: '',
    multiple: false
  }
)
const emit = defineEmits<{
  'update:modelValue': [value: T | T[] | undefined]
  change: [item: Candidate | Candidate[] | undefined]
  loaded: [items: Candidate[]]
}>()
const allList = ref<Candidate[]>([])
const loading = ref(false),
  error = ref(''),
  query = ref(''),
  loaded = ref(false)
let sequence = 0
const sameId = (a: unknown, b: unknown) => a != null && b != null && String(a) === String(b)
const hasValue = computed(() =>
  Array.isArray(props.modelValue)
    ? props.modelValue.length > 0
    : props.modelValue != null && props.modelValue !== ''
)
const missingSelections = computed(() => {
  const ids = Array.isArray(props.modelValue)
    ? props.modelValue
    : hasValue.value
      ? [props.modelValue!]
      : []
  return ids.filter((id) => !filteredList.value.some((item) => sameId(item.id, id)))
})
const missingSelection = computed(() => missingSelections.value.length > 0)
const selectValue = computed({
  get: () =>
    props.multiple
      ? Array.isArray(props.modelValue)
        ? props.modelValue.map((id) => allList.value.find((item) => sameId(item.id, id))?.id ?? id)
        : []
      : (allList.value.find((item) => sameId(item.id, props.modelValue))?.id ?? props.modelValue),
  set: (value) =>
    emit(
      'update:modelValue',
      value == null || (value === '' && !props.preserveEmptyValue) ? undefined : (value as T | T[])
    )
})
const filteredList = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  return allList.value.filter(
    (item) =>
      (props.deptId == null || sameId(item.deptId, props.deptId)) &&
      (!keyword ||
        [item.nickname, item.username, item.deptName].some((value) =>
          value?.toLowerCase().includes(keyword)
        ))
  )
})
const handleFilter = (value: string) => {
  query.value = value
}
const handleChange = (id?: string | number | (string | number)[]) =>
  emit(
    'change',
    props.multiple
      ? allList.value.filter((item) =>
          (Array.isArray(id) ? id : []).some((value) => sameId(value, item.id))
        )
      : allList.value.find((item) => sameId(item.id, id))
  )
async function load() {
  if (props.disabled || loading.value) return
  const current = ++sequence
  loading.value = true
  error.value = ''
  try {
    const items = await systemUserSelection.active()
    if (current !== sequence) return
    allList.value = items.filter((item) => item.status == null || item.status === 0)
    loaded.value = true
    emit('loaded', allList.value)
  } catch {
    if (current === sequence) error.value = '用户候选加载失败，已选用户保留。'
  } finally {
    if (current === sequence) loading.value = false
  }
}
function open(visible: boolean) {
  if (visible) {
    query.value = ''
    if (!loaded.value) void load()
  }
}
watch(
  () => props.contextKey,
  () => {
    sequence++
    allList.value = []
    loaded.value = false
    loading.value = false
    error.value = ''
    query.value = ''
  }
)
watch(
  () => [props.modelValue, props.disabled, props.contextKey],
  () => {
    if (hasValue.value && !props.currentLabel && !loaded.value) void load()
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  sequence++
})
</script>
<style scoped>
.user-select {
  width: 100%;
  min-width: 0;
}

.user-select-error {
  margin: 6px 0 0;
  line-height: 1.5;
  color: var(--el-color-danger);
}
</style>
