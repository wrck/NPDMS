import axios from 'axios'
import { ref } from 'vue'
import { getCollectionEvidence } from '@/api/collection-evidence'
import type { CollectionEvidence, EvidenceLocation } from '@/types/collection-evidence'

export function useCollectionEvidence() {
  const evidence = ref<CollectionEvidence>()
  const status = ref<'IDLE' | 'LOADING' | 'READY' | 'ERROR' | 'RESTRICTED'>('IDLE')
  let controller: AbortController | undefined
  let generation = 0
  function reset() {
    generation += 1
    controller?.abort()
    controller = undefined
    evidence.value = undefined
    status.value = 'IDLE'
  }
  async function load(location: EvidenceLocation) {
    reset()
    const current = generation
    const request = new AbortController()
    controller = request
    status.value = 'LOADING'
    try {
      const response = await getCollectionEvidence({ ...location }, request.signal)
      if (request.signal.aborted || current !== generation) return
      evidence.value = response
      status.value = 'READY'
    } catch (error) {
      if (request.signal.aborted || current !== generation) return
      status.value = axios.isAxiosError(error) && error.response?.status === 403 ? 'RESTRICTED' : 'ERROR'
    } finally {
      if (controller === request) controller = undefined
    }
  }
  return { evidence, status, load, reset }
}
