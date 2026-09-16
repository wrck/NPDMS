// src/api/lowcode-version.ts
import { get, post } from '@/utils/request'

export interface LowCodeConfigVersion {
  id: number
  configType: string
  configId: number
  configCode: string
  version: number
  snapshot: string
  changeLog?: string
  status: string
  environment: string
  createTime: string
  createBy: string
  parentVersionId?: number | null
  branch?: string
  tags?: string
}

export interface DiffEntry {
  changeType: 'ADDED' | 'REMOVED' | 'MODIFIED'
  fieldPath: string
  oldValue?: string
  newValue?: string
}

export interface VersionDiffDTO {
  fromVersion: number
  toVersion: number
  entries: DiffEntry[]
}

/** 版本树节点（按 parentVersionId 构建分支树，支持多分支） */
export interface VersionTreeNode {
  version: number
  configCode: string
  changeLog?: string
  status: string
  environment: string
  createBy: string
  createTime: string
  children: VersionTreeNode[]
  versionId?: number
  parentVersionId?: number | null
  branch?: string
  tags?: string
}

export function getVersionHistory(configType: string, configId: number) {
  return get<LowCodeConfigVersion[]>('/api/lowcode/version/history', {
    configType,
    configId
  })
}

/** 查询版本树（按 parentVersionId 构建分支树，支持多分支） */
export function getVersionTree(configType: string, configId: number) {
  return get<VersionTreeNode[]>('/api/lowcode/version/tree', {
    configType,
    configId
  })
}

export function diffVersions(
  configType: string,
  configId: number,
  fromVersion: number,
  toVersion: number
) {
  return get<VersionDiffDTO>('/api/lowcode/version/diff', {
    configType,
    configId,
    fromVersion,
    toVersion
  })
}

export function rollbackVersion(
  configType: string,
  configId: number,
  targetVersion: number,
  changeLog?: string
) {
  return post<LowCodeConfigVersion>('/api/lowcode/version/rollback', null, {
    params: { configType, configId, targetVersion, changeLog }
  })
}

export function promoteConfig(targetEnvironment: string, configCodes: string[]) {
  return post<void>('/api/lowcode/version/promote', configCodes, {
    params: { targetEnvironment }
  })
}

export function exportPackage(configCodes: string[]) {
  return get<string>('/api/lowcode/version/export-package', {
    configCodes: configCodes.join(',')
  })
}

/** 导出配置包（zip 二进制） */
export function exportPackageZip(configCodes: string[], targetEnvironment: string) {
  return post<Blob>('/api/lowcode/version/export-package', { configCodes, targetEnvironment }, { responseType: 'blob' })
}

/** 导入配置包 */
export function importPackage(file: File, overwrite: boolean) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('overwrite', String(overwrite))
  return post('/api/lowcode/version/import-package', formData)
}

/** 创建分支（基于指定版本创建新分支） */
export function createBranch(params: {
  configType: string
  configId: number
  baseVersionId: number
  branchName: string
  changeLog?: string
}) {
  return post<LowCodeConfigVersion>('/api/lowcode/version/branch', params)
}

/** 为版本添加标签 */
export function addTag(params: {
  configType: string
  configId: number
  versionId: number
  tag: string
}) {
  return post<LowCodeConfigVersion>('/api/lowcode/version/tag', params)
}

/** 版本简报（管道图中的环境列卡片用） */
export interface VersionBrief {
  version: number
  status: string
  changeLog?: string
  createBy?: string
  createTime?: string
}

/** 门禁简报 */
export interface GateBrief {
  passed: boolean
  failureCount: number
  failureSummaries: string[]
}

/** 晋升管道状态（单 configCode 一项） */
export interface PromotionPipelineDTO {
  configCode: string
  configType?: string
  devVersion?: VersionBrief | null
  testVersion?: VersionBrief | null
  prodVersion?: VersionBrief | null
  devToTestGate: GateBrief
  testToProdGate: GateBrief
}

/** 门禁检查结果（后端 PromotionGateService.GateResult 对应） */
export interface GateResult {
  passed: boolean
  failures: Array<{ rule: string; reason: string; configCode?: string }>
  sourceEnvironment: string
  targetEnvironment: string
  configCodes: string[]
}

/** 查询晋升管道状态（多个 configCode） */
export function getPipelineStatus(configCodes: string[]) {
  return get<PromotionPipelineDTO[]>('/api/lowcode/version/pipeline', {
    configCodes: configCodes.join(',')
  })
}

/** 晋升门禁预检（不实际晋升） */
export function checkPromotionGate(params: {
  sourceEnvironment: string
  targetEnvironment: string
  configCodes: string[]
}) {
  return post<GateResult>('/api/lowcode/version/gate-check', params)
}

/** 导入冲突项 */
export interface ImportConflictItem {
  configType: string
  configId: number
  configCode: string
  sourceVersion?: number
  sourceChangeLog?: string
  sourceCreateBy?: string
  sourceCreateTime?: string
  targetVersion?: number
  targetChangeLog?: string
  targetCreateBy?: string
  targetCreateTime?: string
  /** 解决方式: KEEP_SOURCE | KEEP_TARGET | SKIP（null 表示待选择） */
  resolution?: string | null
}

/** 导入冲突检测结果 */
export interface ImportConflictDTO {
  sourceEnvironment: string
  targetEnvironment: string
  conflicts: ImportConflictItem[]
  noConflictCount: number
  totalCount: number
}

/** 检测导入冲突 */
export function detectImportConflicts(packageJson: string, targetEnvironment: string) {
  return post<ImportConflictDTO>('/api/lowcode/version/import-conflicts', {
    packageJson,
    targetEnvironment
  })
}

/** 按解决方案导入配置包 */
export function importWithResolution(
  packageJson: string,
  targetEnvironment: string,
  resolutions: Record<string, string>
) {
  return post<void>('/api/lowcode/version/import-resolve', {
    packageJson,
    targetEnvironment,
    resolutions
  })
}

/** 读取上传文件为文本 */
export function readFileAsText(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(reader.error)
    reader.readAsText(file)
  })
}

/** 发布影响项 */
export interface ImpactItem {
  configType: string
  configId: number
  configCode: string
  referenceField: string
  status?: string
  severity: 'HIGH' | 'MEDIUM' | 'LOW' | string
}

/** 发布影响范围分析结果 */
export interface PublishImpactDTO {
  sourceConfigType: string
  sourceConfigId: number
  sourceConfigCode?: string
  sourceVersion?: number
  impactedConfigs: ImpactItem[]
  totalImpacted: number
}

/** 回滚预览（对比当前版本与目标版本差异） */
export function getRollbackPreview(configType: string, configId: number, targetVersion: number) {
  return get<VersionDiffDTO>('/api/lowcode/version/rollback-preview', {
    configType,
    configId,
    targetVersion
  })
}

/** 发布影响范围分析 */
export function getPublishImpact(configType: string, configId: number, configCode?: string) {
  return get<PublishImpactDTO>('/api/lowcode/version/publish-impact', {
    configType,
    configId,
    configCode
  })
}
