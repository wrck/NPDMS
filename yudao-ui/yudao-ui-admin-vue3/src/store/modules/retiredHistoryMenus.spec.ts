import { describe, expect, it } from 'vitest'
import { withoutRetiredHistoryMenus } from './retiredHistoryMenus'

const menu = (component: string, path: string): AppCustomRouteRecordRaw => ({
  component, path, name: path, icon: '', meta: {}, redirect: '', visible: true
})

describe('retired history menu entries', () => {
  it('removes retired routes at every depth without redirecting or mutating cached grants', () => {
    const current = menu('pms/project/project-master-detail/index', 'project-master-detail')
    const source = [{ ...menu('', '/pms'), children: [
      menu('pms/engineering/schedule-backward/index', 'sol-schedule-backward'),
      { ...menu('', 'governance'), children: [
        menu('pms/project/project-governance/index', 'project-governance'), current
      ] }
    ] }]
    const result = withoutRetiredHistoryMenus(source)
    expect(result[0].children).toHaveLength(1)
    expect(result[0].children![0].children).toEqual([current])
    expect(current.redirect).toBe('')
    expect(source[0].children).toHaveLength(2)
    expect(source[0].children[1].children).toHaveLength(2)
  })

  it('retains current construction planning, governance and unrelated routes', () => {
    const source = [menu('pms/project/project-master-detail/index', 'detail'),
      menu('pms/engineering/construction-plan/index', 'construction-plan'),
      menu('pms/project/project-governance-v2/index', 'other')]
    expect(withoutRetiredHistoryMenus(source)).toEqual(source)
    expect(withoutRetiredHistoryMenus([])).toEqual([])
  })
})
