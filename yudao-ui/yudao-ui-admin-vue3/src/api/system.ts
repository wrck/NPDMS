import { del, get, post, put } from '@/utils/request'

/** Common pagination query parameters */
export interface PageQuery {
  page?: number
  size?: number
  keyword?: string
}

/** Common paginated result envelope */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

// ===================== User =====================

export interface SysUser {
  id?: number
  username: string
  nickname: string
  email?: string
  phone?: string
  deptId?: number
  deptName?: string
  status?: number
  roleIds?: number[]
  remark?: string
  createTime?: string
}

export interface UserPageQuery extends PageQuery {
  deptId?: number
  status?: number
}

export function getUserPage(params: UserPageQuery): Promise<PageResult<SysUser>> {
  return get<PageResult<SysUser>>('/api/system/user/page', params)
}

/** @提及自动补全用的轻量用户搜索结果（仅含 id/username/realName） */
export interface MentionUser {
  id: number
  username: string
  realName?: string
}

/**
 * 用户搜索（@提及自动补全用）。
 *
 * <p>仅需登录（无需 system:user:list 权限），按用户名/真实姓名模糊匹配，
 * 返回最多 20 条，仅包含 id/username/realName 字段。</p>
 *
 * @param keyword 关键词（用户名或真实姓名）
 * @param limit   最大返回条数，默认 20，上限 50
 */
export function searchUsers(keyword?: string, limit = 20): Promise<MentionUser[]> {
  return get<MentionUser[]>('/api/system/user/search', { keyword, limit })
}

export function getUserById(id: number): Promise<SysUser> {
  return get<SysUser>(`/api/system/user/${id}`)
}

export function createUser(data: SysUser): Promise<SysUser> {
  return post<SysUser>('/api/system/user', data)
}

export function updateUser(data: SysUser): Promise<SysUser> {
  return put<SysUser>('/api/system/user', data)
}

export function deleteUser(id: number): Promise<void> {
  return del<void>(`/api/system/user/${id}`)
}

// TODO: 后端 SysUserController 暂无重置密码专用端点（无 /password 路径）。
// 如需重置密码，请走 PUT /api/system/user 更新接口，或等待后端补充专用端点。
// export function resetUserPassword(id: number, password: string): Promise<void> {
//   return put<void>(`/api/system/users/${id}/password`, { password })
// }

// ===================== Role =====================

export interface SysRole {
  id?: number
  roleName: string
  roleCode: string
  description?: string
  status?: string
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  menuIds?: number[]
  dataScope?: string
}

export function getRolePage(params: { page: number; size: number; roleName?: string }): Promise<PageResult<SysRole>> {
  return get<PageResult<SysRole>>('/api/system/role/page', params)
}

export function getRoleAll(): Promise<SysRole[]> {
  return get<SysRole[]>('/api/system/role/all')
}

/** 角色下拉选项（与后端 SysRole 的 roleName/roleCode 字段对齐） */
export interface RoleOption {
  id?: number
  /** 角色名称（后端 roleName） */
  roleName: string
  /** 角色编码（后端 roleCode） */
  roleCode: string
}

/**
 * 全部角色列表（审批链配置等场景下拉用）。
 *
 * <p>仅需登录（无需 system:role:list 权限），返回 id/roleName/roleCode 字段。
 * 调用后端实际路径 /api/system/role/all。</p>
 */
export function getAllRoles(): Promise<RoleOption[]> {
  return get<RoleOption[]>('/api/system/role/all')
}

export function createRole(data: SysRole): Promise<SysRole> {
  return post<SysRole>('/api/system/role', data)
}

export function updateRole(data: SysRole): Promise<SysRole> {
  return put<SysRole>('/api/system/role', data)
}

export function deleteRole(id: number): Promise<void> {
  return del<void>(`/api/system/role/${id}`)
}

// ===================== Menu =====================

export interface SysMenu {
  id?: number
  parentId: number
  name: string
  path?: string
  component?: string
  icon?: string
  type: number // 0=directory 1=menu 2=button 3=lowcode page
  permission?: string
  sort?: number
  visible?: number
  status?: number
  /** 低代码页面类型（type=3 时使用）：form / list / tab / related-page */
  pageType?: string
  /** 低代码配置编码（type=3 时使用） */
  pageCode?: string
  children?: SysMenu[]
  createTime?: string
}

export function getMenuTree(): Promise<SysMenu[]> {
  return get<SysMenu[]>('/api/system/menu/tree')
}

export function createMenu(data: SysMenu): Promise<SysMenu> {
  return post<SysMenu>('/api/system/menu', data)
}

export function updateMenu(data: SysMenu): Promise<SysMenu> {
  return put<SysMenu>('/api/system/menu', data)
}

export function deleteMenu(id: number): Promise<void> {
  return del<void>(`/api/system/menu/${id}`)
}

// ===================== Dict =====================

export interface SysDict {
  id?: number
  dictType: string
  dictName: string
  status?: string
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
}

export interface SysDictItem {
  id?: number
  dictId: number
  itemText: string
  itemValue: string
  sortOrder?: number
  status?: string
  createTime?: string
  updateTime?: string
}

export function getDictPage(params: { page: number; size: number; dictName?: string }): Promise<PageResult<SysDict>> {
  return get<PageResult<SysDict>>('/api/system/dict/page', params)
}

export function createDict(data: SysDict): Promise<SysDict> {
  return post<SysDict>('/api/system/dict', data)
}

export function updateDict(data: SysDict): Promise<SysDict> {
  return put<SysDict>('/api/system/dict', data)
}

export function deleteDict(id: number): Promise<void> {
  return del<void>(`/api/system/dict/${id}`)
}

export function getDictItems(dictType: string): Promise<SysDictItem[]> {
  return get<SysDictItem[]>(`/api/system/dict/items/${dictType}`)
}

export function createDictItem(data: SysDictItem): Promise<SysDictItem> {
  return post<SysDictItem>('/api/system/dict/item', data)
}

export function updateDictItem(data: SysDictItem): Promise<SysDictItem> {
  return put<SysDictItem>('/api/system/dict/item', data)
}

export function deleteDictItem(id: number): Promise<void> {
  return del<void>(`/api/system/dict/item/${id}`)
}
