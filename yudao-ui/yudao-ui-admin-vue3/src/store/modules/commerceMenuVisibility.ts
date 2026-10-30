// 仅控制工作台菜单展示，保留路由和服务端既有权限。
export const commerceMenusForRoles = <T extends { id?: number | string; visible?: boolean; children?: T[] }>(menus: T[], roles: string[]): T[] => {
  if (roles.includes('super_admin') || !roles.some(role => ['PROJECT_MANAGER', 'SERVICE_MANAGER'].includes(role))) return menus
  return menus.map(menu => ({
    ...menu,
    ...(String(menu.id) === '930900' || String(menu.id) === '930903' ? { visible: false } : {}),
    ...(menu.children ? { children: commerceMenusForRoles(menu.children, roles) } : {})
  }))
}
