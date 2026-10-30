import { computed, onBeforeUnmount, ref } from 'vue'
import { getProjectContext, type ProjectContext, type ProjectSelectionId } from '@/api/pms/project/selection'

/** 两类任务共用项目资料的请求、错误处理及过期响应保护。 */
export function useProjectInformation() {
  const context = ref<ProjectContext | null>(null)
  const loading = ref(false)
  const error = ref('')
  let generation = 0
  const load = async (projectId?: ProjectSelectionId | null) => {
    const current = ++generation
    context.value = null
    error.value = ''
    loading.value = false
    if (!projectId) return null
    loading.value = true
    try {
      const value = await getProjectContext(projectId)
      if (current !== generation) return
      context.value = value
      return value
    } catch {
      if (current === generation) error.value = '项目信息加载失败，请重新选择或查询项目。'
    } finally {
      if (current === generation) loading.value = false
    }
  }
  onBeforeUnmount(() => { ++generation })
  return { context, project: computed(() => context.value?.project ?? null),
    customerServiceLevel: computed(() => context.value?.customerServiceLevel), loading, error, load }
}
