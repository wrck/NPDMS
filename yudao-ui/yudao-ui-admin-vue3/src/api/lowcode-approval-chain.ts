import { del, get, post, put } from '@/utils/request'

/** 审批级别（levels JSON 数组中的一项） */
export interface ApprovalLevel {
  /** 级别序号（从 1 开始） */
  level: number
  /** 审批角色编码（对应 sys_role.role_code，如 admin / manager） */
  approverRole: string
  /** 级别名称（如"主管审批"） */
  name: string
}

/** 多级审批链 */
export interface LowCodeApprovalChain {
  id?: number
  /** 配置类型: FORM/LIST/ENTITY/MICROFLOW/CONNECTOR/RULE/TAB/RELATED_PAGE */
  configType: string
  /** 审批链名称 */
  name: string
  /** 审批级别 JSON 字符串: [{level:1, approverRole:"admin", name:"主管审批"}] */
  levels: string
  /** 是否启用：1=启用 / 0=停用 */
  enabled?: number
  createTime?: string
  updateTime?: string
}

/** 按配置类型查询审批链列表 */
export function getApprovalChainsByConfigType(configType: string): Promise<LowCodeApprovalChain[]> {
  return get<LowCodeApprovalChain[]>(`/api/lowcode/approval-chain/config-type/${configType}`)
}

/** 查询全部审批链 */
export function getApprovalChainList(): Promise<LowCodeApprovalChain[]> {
  return get<LowCodeApprovalChain[]>('/api/lowcode/approval-chain')
}

/** 审批链详情 */
export function getApprovalChain(id: number): Promise<LowCodeApprovalChain> {
  return get<LowCodeApprovalChain>(`/api/lowcode/approval-chain/${id}`)
}

/** 新建审批链 */
export function createApprovalChain(data: LowCodeApprovalChain): Promise<LowCodeApprovalChain> {
  return post<LowCodeApprovalChain>('/api/lowcode/approval-chain', data)
}

/** 更新审批链 */
export function updateApprovalChain(id: number, data: LowCodeApprovalChain): Promise<void> {
  return put<void>(`/api/lowcode/approval-chain/${id}`, data)
}

/** 删除审批链 */
export function deleteApprovalChain(id: number): Promise<void> {
  return del<void>(`/api/lowcode/approval-chain/${id}`)
}

/** 解析 levels JSON 字符串为级别数组（容错：空串/非法 JSON 返回空数组） */
export function parseLevels(levelsJson?: string): ApprovalLevel[] {
  if (!levelsJson) return []
  try {
    const parsed = JSON.parse(levelsJson) as ApprovalLevel[]
    if (!Array.isArray(parsed)) return []
    return parsed.sort((a, b) => (a.level ?? 0) - (b.level ?? 0))
  } catch {
    return []
  }
}

/** 将级别数组序列化为 levels JSON 字符串 */
export function serializeLevels(levels: ApprovalLevel[]): string {
  return JSON.stringify(levels, null, 2)
}
