import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue'
import * as ProjectsApi from '@/api/pms/project/projects'
import { getDeliveryScopeVersion } from '@/api/pms/commerce'
import { previewContractScope, synchronizeContractScope, contractScopeError, type ContractScopePreview, type ContractScopeVersions } from '@/api/pms/project/project-splits/contract-scope'
import { createSubmissionIdempotencyState } from '@/views/pms/project/projects/submissionIdempotency'
import { splitErrorMessage } from './useProjectSplitWorkspace'

export function useProjectContractScopeSync(projectId: Ref<number>, onSuccess: () => void) {
  const preview = ref<ContractScopePreview>(), versions = ref<ContractScopeVersions>()
  const loading = ref(false), submitting = ref(false), completed = ref(false), error = ref('')
  const busy = computed(() => loading.value || submitting.value)
  const idempotency = createSubmissionIdempotencyState()
  let generation = 0
  const refresh = async () => {
    if (busy.value) return
    const current = ++generation, id = projectId.value
    loading.value = true; preview.value = undefined; versions.value = undefined; error.value = ''; completed.value = false
    try {
      const [project, tree, scopeVersion] = await Promise.all([
        ProjectsApi.getProject(id), ProjectsApi.queryTree(id, { queryType: 'LOCATE', pageSize: 1 }), getDeliveryScopeVersion(id)
      ])
      if (current !== generation) return
      if (tree.updating || project.version == null) throw new Error('项目关系正在更新，请稍后重试')
      const request = { expectedProjectVersion: project.version, expectedTreeVersion: tree.treeVersion, expectedScopeVersion: scopeVersion }
      const result = await previewContractScope(id, request)
      if (current !== generation) return
      versions.value = request; preview.value = result
    } catch (failure) { if (current === generation) error.value = contractScopeError(splitErrorMessage(failure)) }
    finally { if (current === generation) loading.value = false }
  }
  const submit = async () => {
    if (busy.value || completed.value || !versions.value || !preview.value?.valid || !preview.value.changedLineCount) return
    const current = generation, id = projectId.value
    const request = { ...versions.value, sourceDigest: preview.value.sourceDigest }
    submitting.value = true; error.value = ''
    try {
      await synchronizeContractScope(id, request, idempotency.keyFor({ projectId: id, ...request }))
      if (current !== generation) return
      completed.value = true; onSuccess()
    } catch (failure) { if (current === generation) error.value = contractScopeError(splitErrorMessage(failure)) }
    finally { if (current === generation) submitting.value = false }
  }
  watch(projectId, () => {
    generation++; loading.value = false; submitting.value = false; idempotency.reset(); refresh()
  }, { immediate: true })
  onBeforeUnmount(() => { generation++ })
  return { preview, loading, submitting, busy, completed, error, refresh, submit }
}
