import { onBeforeUnmount, ref } from 'vue'
import { searchProjectOptions, type ProjectOption } from '@/api/pms/project/selection'

export function useProjectOptions(selectedId: () => string) {
  const options = ref<ProjectOption[]>([])
  const loading = ref(false)
  const error = ref('')
  let generation = 0
  const reset = () => { ++generation; options.value = []; loading.value = false; error.value = '' }
  const search = async (keyword = '') => {
    const current = ++generation
    loading.value = true
    error.value = ''
    try {
      const candidates = await searchProjectOptions(keyword.trim())
      if (current !== generation) return
      const selected = options.value.find(option => String(option.id) === selectedId())
      options.value = selected && !candidates.some(option => String(option.id) === selectedId())
        ? [selected, ...candidates] : candidates
    } catch {
      if (current === generation) error.value = '项目候选加载失败，请重新输入项目名称查找。'
    } finally { if (current === generation) loading.value = false }
  }
  onBeforeUnmount(() => { ++generation })
  return { options, loading, error, search, reset }
}
