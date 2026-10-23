const stagePlanMenuRoles = new Set([
  'super_admin',
  'admin',
  'engineeManager',
  'engineeManagerLeader'
])

/** 仅控制独立菜单展示；项目详情重排继续使用原路由和服务端权限。 */
export const stagePlanMenusForRoles = <
  T extends { component?: string; visible?: boolean; children?: T[] }
>(
  menus: T[],
  roles: string[]
): T[] => {
  if (roles.some((role) => stagePlanMenuRoles.has(role))) return menus
  return menus.map((menu) => ({
    ...menu,
    ...(menu.component === 'pms/engineering/stage-plan/index' ? { visible: false } : {}),
    ...(menu.children ? { children: stagePlanMenusForRoles(menu.children, roles) } : {})
  }))
}
