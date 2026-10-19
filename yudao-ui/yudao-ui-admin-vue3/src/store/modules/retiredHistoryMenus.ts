const retiredComponents = new Set([
  'pms/engineering/schedule-backward/index',
  'pms/project/project-governance/index'
])

/** Retire only the two legacy pages; existing grants and current business routes stay intact. */
export const withoutRetiredHistoryMenus = (menus: AppCustomRouteRecordRaw[]): AppCustomRouteRecordRaw[] =>
  menus.filter(menu => !retiredComponents.has(menu.component)).map(menu => menu.children
    ? { ...menu, children: withoutRetiredHistoryMenus(menu.children) }
    : menu)
