/**
 * API 类型契约测试（type-level）。
 *
 * <p>本文件不产生运行时代码，仅在 {@code vue-tsc --noEmit} 时被 TypeScript
 * 编译器检查。若任一断言失败，编译将报错，从而在 CI 阶段拦截类型回归。</p>
 *
 * <p>覆盖范围：</p>
 * <ul>
 *   <li>{@link Result} / {@link PageResult} 信封结构</li>
 *   <li>核心业务实体（Project / Asset / Milestone / Issue / Risk 等）必填字段</li>
 *   <li>{@link EpTagType} 联合类型取值</li>
 *   <li>工具类型（Optional / Nullable / DeepPartial / WithRequired）</li>
 * </ul>
 */
import type {
  Result,
  PageResult,
  Project,
  Asset,
  Milestone,
  Issue,
  Risk,
  ChangeRequest,
  Notification,
  Attachment,
  SysDict,
  SysDictItem
} from '../api'
import type {
  EpTagType,
  Optional,
  Nullable,
  DeepPartial,
  WithOptional,
  WithRequired
} from '../index'

// =========================================================================
// 辅助类型：编译期断言。若 T 不为 true 则产生 never，赋值给 1 时报错。
// =========================================================================

/** 严格类型相等断言 */
type Equals<A, B> =
  (<T>() => T extends A ? 1 : 2) extends <T>() => T extends B ? 1 : 2 ? true : false

function assertType<T>(value: T): T {
  return value
}

// =========================================================================
// 1. Result<T> 信封结构
// =========================================================================

const _resultOk: Result<string> = {
  code: 200,
  message: 'ok',
  data: 'payload',
  success: true
}

const _resultWithTimestamp: Result<number> = {
  code: 200,
  message: 'ok',
  data: 42,
  success: true,
  timestamp: Date.now()
}

// Result.data 类型应与泛型参数一致
const _dataIsString: string = _resultOk.data
const _dataIsNumber: number = _resultWithTimestamp.data

// =========================================================================
// 2. PageResult<T> 分页结构
// =========================================================================

const _page: PageResult<Project> = {
  records: [],
  total: 0,
  size: 10,
  current: 1,
  pages: 0
}

const _records: Project[] = _page.records
const _total: number = _page.total

// =========================================================================
// 3. EpTagType 联合类型
// =========================================================================

const _tagPrimary: EpTagType = 'primary'
const _tagSuccess: EpTagType = 'success'
const _tagWarning: EpTagType = 'warning'
const _tagDanger: EpTagType = 'danger'
const _tagInfo: EpTagType = 'info'

// @ts-expect-error —— EpTagType 不包含 'error'
const _tagError: EpTagType = 'error'

// =========================================================================
// 4. 业务实体必填字段验证
// =========================================================================

const _project: Project = {
  id: 1,
  projectCode: 'P001',
  projectName: '网络设备项目',
  projectType: 'NETWORK_DEVICE',
  status: 'IN_PROGRESS',
  customerId: 100,
  startDate: '2024-01-01',
  endDate: '2024-12-31',
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z'
}

const _asset: Asset = {
  id: 1,
  assetNo: 'A001',
  serialNo: 'SN001',
  projectId: 1,
  status: 'IN_STOCK',
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z'
}

const _milestone: Milestone = {
  id: 1,
  projectId: 1,
  milestoneType: 'START',
  plannedDate: '2024-01-01',
  status: 'PENDING'
}

const _issue: Issue = {
  id: 1,
  issueNo: 'ISS001',
  projectId: 1,
  description: '描述',
  priority: 'HIGH',
  status: 'OPEN'
}

const _risk: Risk = {
  id: 1,
  riskNo: 'R001',
  projectId: 1,
  description: '风险描述',
  category: 'TECHNICAL',
  likelihood: 3,
  impact: 4,
  score: 12,
  priority: 'HIGH',
  status: 'OPEN'
}

const _changeRequest: ChangeRequest = {
  id: 1,
  crNo: 'CR001',
  projectId: 1,
  title: '变更标题',
  priority: 'MEDIUM',
  status: 'PENDING'
}

const _notification: Notification = {
  id: 1,
  userId: 1,
  title: '通知标题',
  content: '通知内容',
  category: 'MILESTONE',
  readStatus: 'UNREAD',
  createdAt: '2024-01-01T00:00:00Z'
}

const _attachment: Attachment = {
  id: 1,
  bizType: 'PROJECT',
  bizId: 1,
  fileName: 'file.pdf',
  fileSize: 1024,
  mimeType: 'application/pdf',
  storagePath: '/files/file.pdf',
  storageType: 'LOCAL',
  uploadUserId: 1,
  uploadTime: '2024-01-01T00:00:00Z'
}

// =========================================================================
// 5. 字典实体
// =========================================================================

const _dict: SysDict = {
  id: 1,
  dictCode: 'asset_status',
  dictName: '资产状态',
  status: 'ENABLED'
}

const _dictItem: SysDictItem = {
  id: 1,
  dictCode: 'asset_status',
  itemValue: 'IN_STOCK',
  itemLabel: '在库',
  sort: 1,
  status: 'ENABLED'
}

// =========================================================================
// 6. 工具类型
// =========================================================================

interface Sample {
  a: string
  b: number
  c: boolean
}

// Optional<T> —— 所有属性变可选
const _optional: Optional<Sample> = { a: 'x' }

// Nullable<T> —— 所有属性允许 null
const _nullable: Nullable<Sample> = { a: null as unknown as string, b: 1, c: true }

// DeepPartial<T> —— 深度可选
const _deepPartial: DeepPartial<Sample> = {}

// WithOptional<T, K> —— 指定键变可选
const _withOptional: WithOptional<Sample, 'a'> = { b: 1, c: true }

// WithRequired<T, K> —— 指定键变必选
interface PartialSample {
  a?: string
  b: number
}
const _withRequired: WithRequired<PartialSample, 'a'> = { a: 'required', b: 1 }

// =========================================================================
// 7. 类型相等断言
// =========================================================================

type _AssertResultData = Equals<Result<string>['data'], string>
const _assertResultData: _AssertResultData = true

type _AssertPageRecords = Equals<PageResult<Project>['records'], Project[]>
const _assertPageRecords: _AssertPageRecords = true

// 防止「未使用变量」告警（编译期保留）
export const _typeTests = assertType({
  _resultOk,
  _resultWithTimestamp,
  _page,
  _tagPrimary,
  _project,
  _asset,
  _milestone,
  _issue,
  _risk,
  _changeRequest,
  _notification,
  _attachment,
  _dict,
  _dictItem,
  _optional,
  _nullable,
  _deepPartial,
  _withOptional,
  _withRequired,
  _assertResultData,
  _assertPageRecords
})
