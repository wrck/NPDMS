import type { FileId, FileLifecycleResultVO } from '@/api/pms/platform/file'

export interface FileSelection {
  artifactId: FileId
  versionNo: number
  referenceId: FileId
  referenceKey: string
}

export interface DetachedFileSlot extends FileLifecycleResultVO {
  referenceKey: string
}
