<template>
  <el-select
    v-model="selectedValue"
    :placeholder="placeholder"
    :clearable="clearable"
    :disabled="disabled"
    :filterable="true"
    :remote="true"
    :remote-method="handleSearch"
    :loading="loading"
    :size="size"
    :multiple="multiple"
    :collapse-tags="multiple"
    :collapse-tags-tooltip="multiple"
    class="w-full"
    @change="handleChange"
  >
    <el-option
      v-for="item in options"
      :key="item[valueField]"
      :label="getDisplayLabel(item)"
      :value="item[valueField]"
    />
  </el-select>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'

defineOptions({ name: 'PmsEntitySelect' })

const props = defineProps({
  modelValue: {
    type: [Number, String, Array],
    default: undefined
  },
  /** 查询 API 函数，接收 params 对象，返回 { list: T[] } */
  api: {
    type: Function as any,
    required: true
  },
  /** 选项数组（静态模式，与 api 二选一） */
  options: {
    type: Array as any,
    default: () => []
  },
  /** 显示字段名或字段数组（多字段拼接显示） */
  labelField: {
    type: [String, Array] as any,
    default: 'name'
  },
  /** 值字段名 */
  valueField: {
    type: String,
    default: 'id'
  },
  /** 远程搜索时传递给 API 的查询参数名 */
  queryField: {
    type: String,
    default: 'name'
  },
  placeholder: {
    type: String,
    default: '请选择'
  },
  clearable: {
    type: Boolean,
    default: true
  },
  disabled: {
    type: Boolean,
    default: false
  },
  size: {
    type: String,
    default: 'default'
  },
  /** 是否多选 */
  multiple: {
    type: Boolean,
    default: false
  },
  /** 额外查询参数 */
  extraParams: {
    type: Object as any,
    default: () => ({})
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const selectedValue = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const options = ref<any[]>([])

/** 获取显示标签：支持多字段拼接 */
const getDisplayLabel = (item: any) => {
  if (Array.isArray(props.labelField)) {
    return props.labelField.map((f: string) => item[f]).filter(Boolean).join(' - ')
  }
  // 支持 "code - name" 格式
  return item[props.labelField] ?? ''
}

/** 远程搜索 */
const handleSearch = async (keyword: string) => {
  if (!props.api) return
  loading.value = true
  try {
    const params = {
      pageNo: 1,
      pageSize: 50,
      [props.queryField]: keyword,
      ...props.extraParams
    }
    const res = await props.api(params)
    options.value = res?.list || res || []
  } catch (e) {
    options.value = []
  } finally {
    loading.value = false
  }
}

/** 值变化时回查当前选中项的完整对象 */
const handleChange = (val: any) => {
  if (props.multiple) {
    const selected = options.value.filter((i: any) =>
      Array.isArray(val) ? val.includes(i[props.valueField]) : false
    )
    emit('change', val, selected)
  } else {
    const selected = options.value.find((i: any) => i[props.valueField] === val)
    emit('change', val, selected)
  }
}

/** 初始加载：若有 modelValue，回查当前选中项 */
const loadInitial = async () => {
  if (props.modelValue) {
    // 有值时加载一次列表，确保选中项可显示
    await handleSearch('')
  } else {
    // 无值时预加载前若干条
    await handleSearch('')
  }
}

watch(
  () => props.modelValue,
  (val) => {
    if (val && options.value.length === 0) {
      loadInitial()
    }
  },
  { immediate: false }
)

watch(
  () => props.extraParams,
  () => {
    handleSearch('')
  },
  { deep: true }
)

onMounted(loadInitial)
</script>
