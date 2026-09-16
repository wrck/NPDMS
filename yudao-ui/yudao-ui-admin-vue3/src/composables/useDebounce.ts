import { ref, watch, type Ref } from 'vue'

export interface UseDebounceReturn<T> {
  /** 实时绑定的输入值 */
  value: Ref<T>
  /** 防抖后的值，延迟 delay 后同步 */
  debouncedValue: Ref<T>
  /** 是否处于防抖等待中 */
  isLoading: Ref<boolean>
}

/**
 * 防抖搜索 composable。
 *
 * 用法：
 *   const { value, debouncedValue, isLoading } = useDebounce('', 300)
 *   watch(debouncedValue, (v) => { query.keyword = v; loadData() })
 *   <el-input v-model="value" />
 *
 * @param initial 初始值
 * @param delay   防抖延迟毫秒，默认 300
 */
export function useDebounce<T>(initial: T, delay = 300): UseDebounceReturn<T> {
  const value = ref(initial) as Ref<T>
  const debouncedValue = ref(initial) as Ref<T>
  const isLoading = ref(false)

  let timer: ReturnType<typeof setTimeout> | null = null

  watch(value, (newVal) => {
    isLoading.value = true
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      debouncedValue.value = newVal
      isLoading.value = false
    }, delay)
  })

  return { value, debouncedValue, isLoading }
}
