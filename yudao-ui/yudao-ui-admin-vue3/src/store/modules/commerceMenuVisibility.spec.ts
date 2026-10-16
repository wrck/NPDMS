import { describe, expect, it } from 'vitest'
import { commerceMenusForRoles } from './commerceMenuVisibility'
const menus = [{ id: 19260, visible: true, children: [{ id: 930900, visible: true }, { id: 930903, visible: true }, { id: 19000, visible: true }] }]
describe('commerce workbench role visibility', () => {
  it.each(['PROJECT_MANAGER', 'SERVICE_MANAGER'])('hides only the two workbenches for %s without mutating the shared menu source', role => {
    const result = commerceMenusForRoles(menus, [role])
    expect(result[0].children?.map(menu => menu.visible)).toEqual([false, false, true])
    expect(menus[0].children.every(menu => menu.visible)).toBe(true)
  })
  it.each([[], ['OTHER_ROLE'], ['super_admin'], ['super_admin', 'PROJECT_MANAGER']])('preserves other and administrator menus: %j', (...roles) => {
    expect(commerceMenusForRoles(menus, roles)).toBe(menus)
  })
})
