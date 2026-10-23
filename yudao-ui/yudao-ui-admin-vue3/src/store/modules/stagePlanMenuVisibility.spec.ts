import { describe, expect, it } from 'vitest'
import { stagePlanMenusForRoles } from './stagePlanMenuVisibility'

interface Menu {
  component: string
  visible: boolean
  path?: string
  children?: Menu[]
}

const menus: Menu[] = [
  {
    component: 'Layout',
    visible: true,
    children: [
      { component: 'pms/engineering/stage-plan/index', visible: true, path: 'imp-stage-plan' },
      { component: 'pms/project/inheritance/projects/index', visible: true, path: 'projects' }
    ]
  }
]

describe('施工计划独立菜单', () => {
  it.each(['super_admin', 'admin', 'engineeManager', 'engineeManagerLeader'])(
    '对已批准角色%s开放',
    (role) => {
      expect(stagePlanMenusForRoles(menus, [role])).toBe(menus)
    }
  )

  it.each(
    [
      [],
      ['PROJECT_MANAGER'],
      ['SERVICE_MANAGER'],
      ['PROJECT_MANAGER', 'SERVICE_MANAGER'],
      ['ENGINEERING_MANAGEMENT_REVIEW']
    ].map((roles) => ({ roles }))
  )('隐藏非授权角色的入口并保留路由及项目入口：$roles', ({ roles }) => {
    const result = stagePlanMenusForRoles(menus, roles)
    expect(result[0].children).toEqual([
      { ...menus[0].children![0], visible: false },
      menus[0].children![1]
    ])
    expect(menus[0].children![0].visible).toBe(true)
  })

  it('兼具项目经理与工程管理角色时保留菜单', () => {
    expect(stagePlanMenusForRoles(menus, ['PROJECT_MANAGER', 'engineeManager'])).toBe(menus)
  })
})
