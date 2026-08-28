import type { FileLifecycleResultVO } from '@/api/pms/platform/file'

export interface FileSelection {
  artifactId: number
  versionNo: number
  referenceId: number
  referenceKey: string
}

export interface DetachedFileSlot extends FileLifecycleResultVO {
  referenceKey: string
}
