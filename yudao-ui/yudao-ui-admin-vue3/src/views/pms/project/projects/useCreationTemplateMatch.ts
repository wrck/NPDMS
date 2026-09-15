import { onBeforeUnmount, ref, watch } from 'vue'
import { matchTemplates, type ProjectMatchTemplatesReqVO, type ProjectMatchTemplatesRespVO } from '@/api/pms/project/projects'

/** Both root creation entries share the same fact invalidation and response ordering. */
export const useCreationTemplateMatch = (input: () => ProjectMatchTemplatesReqVO, request: typeof matchTemplates = matchTemplates) => {
  const matchLoading = ref(false)
  const matchResult = ref<ProjectMatchTemplatesRespVO | null>(null)
  const selectedTemplateRevisionId = ref<number>()
  let generation = 0
  const invalidate = () => {
    ++generation
    matchResult.value = null
    selectedTemplateRevisionId.value = undefined
    matchLoading.value = false
  }
  // https://vuejs.org/guide/essentials/watchers.html#sync-watchers
  watch(input, invalidate, { flush: 'sync' })
  onBeforeUnmount(invalidate)
  const runMatch = async () => {
    invalidate()
    const current = generation
    matchLoading.value = true
    try {
      const result = await request(input())
      if (current === generation) matchResult.value = result
    } finally {
      if (current === generation) matchLoading.value = false
    }
  }
  return { matchLoading, matchResult, selectedTemplateRevisionId, runMatch }
}
