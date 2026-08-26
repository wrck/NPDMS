import { reactive } from 'vue'
import type { FileArtifactVO, FileUploadMode } from '@/api/pms/platform/file'
import type { DetachedFileSlot, FileSelection } from './types'

export interface FileSlotState {
  artifactId?: number
  referenceKey?: string
  referenceVersion?: number
}

export const resolveFileUploadMode = (artifactId?: number): FileUploadMode =>
  artifactId ? 'ADD_VERSION' : 'CREATE_ARTIFACT'

export const useFileSlotState = () => {
  const state = reactive<FileSlotState>({})

  const reset = (artifactId?: number, referenceKey?: string) => {
    state.artifactId = artifactId
    state.referenceKey = referenceKey
    state.referenceVersion = undefined
  }
  const loaded = (artifact: FileArtifactVO) => {
    state.artifactId = artifact.artifactId
    state.referenceKey = artifact.reference.referenceKey
    state.referenceVersion = artifact.reference.referenceVersion
  }
  const uploaded = (selection: FileSelection) => {
    state.artifactId = selection.artifactId
    state.referenceKey = selection.referenceKey
    state.referenceVersion = undefined
  }
  const detached = (result: DetachedFileSlot) => {
    state.artifactId = result.artifactId
    state.referenceKey = result.referenceKey
    state.referenceVersion = result.factVersion
  }

  return { state, reset, loaded, uploaded, detached }
}
