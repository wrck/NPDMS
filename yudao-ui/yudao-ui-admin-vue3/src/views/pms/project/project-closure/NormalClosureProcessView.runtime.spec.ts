import { computed, defineComponent, h, nextTick, provide } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import NormalClosureProcessView from './NormalClosureProcessView.vue'
import {
  approvalPeopleKey,
  type ApprovalPeople
} from '@/views/bpm/processInstance/detail/approvalPeople'
import {
  getNormalClosureApplication,
  type ApplicationDetail
} from '@/api/pms/acceptance/normal-closure'
import {
  mount,
  passthrough,
  textOf,
  type TestNode
} from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/acceptance/normal-closure', () => ({ getNormalClosureApplication: vi.fn() }))
const ID = '2099999999999999999'
const KEY = `PROJECT_NORMAL_CLOSURE:${ID}`
const tables: unknown[] = []
const table = defineComponent({
  props: ['data'],
  setup: (props) => () => {
    tables.push(props.data)
    return h('table', JSON.stringify(props.data))
  }
})
const apps: { unmount: () => void }[] = []
const setup = async (
  id: string,
  people: ApprovalPeople = {
    processInstanceId: 'process-actual',
    businessKey: KEY,
    names: { '9': '申请人甲', '17': '服务经理乙', '18': '材料审核丙' }
  }
) => {
  const page = mount(
    defineComponent({
      setup() {
        provide(
          approvalPeopleKey,
          computed(() => people)
        )
        return () => h(NormalClosureProcessView, { id })
      }
    }),
    {},
    {
      ElDescriptions: passthrough,
      ElDescriptionsItem: passthrough,
      ElSkeleton: passthrough,
      ElTable: table,
      ElTableColumn: passthrough
    }
  )
  apps.push(page.app)
  for (let i = 0; i < 5; i++) {
    await Promise.resolve()
    await nextTick()
  }
  return page
}
afterEach(() => {
  apps.splice(0).forEach((app) => app.unmount())
  vi.resetAllMocks()
  tables.length = 0
})

describe('NormalClosureProcessView', () => {
  it('loads the exact lossless application ID and shows real detail, checks and historical reviews read-only', async () => {
    const checks = [{ code: 'ALL_TASKS_DONE', passed: true, subjectId: '10' }]
    const reviews = [
      {
        id: '31',
        taskId: 'task-31',
        taskDefinitionKey: 'serviceManagerReview',
        reviewerUserId: '17',
        outcome: 'APPROVE',
        reason: '真实审核意见',
        reviewedAt: '2026-09-10 10:00:00'
      }
    ]
    const value: ApplicationDetail = {
      projectId: '10',
      application: {
        id: ID,
        projectId: '10',
        snapshotId: '20',
        status: 'IN_REVIEW',
        fromStage: 'S4',
        closureType: 'NORMAL',
        applicantUserId: '9',
        serviceManagerUserId: '17',
        reviewerUserId: '18',
        processInstanceId: 'process-actual',
        processDefinitionId: 'normal:1:actual',
        processDefinitionKey: 'PROJECT_NORMAL_CLOSURE',
        businessKey: KEY,
        processEvidence:
          '{"nodes":[{"taskDefinitionKey":"materialReview","candidateUserId":"18"}]}',
        submittedAt: '2026-09-10 09:00:00'
      },
      snapshot: {
        id: '20',
        projectVersion: 4,
        treeVersion: 8,
        fromStage: 'S4',
        passed: true,
        checkedAt: '2026-09-10 08:00:00',
        checkedBy: '9',
        sourceDigest: 'digest',
        evidence: JSON.stringify(checks)
      },
      reviews
    }
    vi.mocked(getNormalClosureApplication).mockResolvedValue(value)
    const page = await setup(KEY)
    expect(getNormalClosureApplication).toHaveBeenCalledExactlyOnceWith(ID)
    const text = textOf(page.root)
    for (const expected of [
      ID,
      'S4',
      '审批中',
      'process-actual',
      'normal:1:actual',
      'materialReview',
      '真实审核意见'
    ])
      expect(text).toContain(expected)
    expect(text).toContain('申请人甲')
    expect(text).toContain('服务经理乙')
    expect(text).toContain('材料审核丙')
    expect(tables).toContainEqual([
      expect.objectContaining({ code: 'ALL_TASKS_DONE', label: '必需任务均已完成', passed: true })
    ])
    expect(tables).toContainEqual([
      expect.objectContaining({
        taskId: 'task-31',
        nodeName: '服务经理审核',
        reviewerName: '服务经理乙'
      })
    ])
    const findDetails = (node: TestNode): TestNode | undefined =>
      node.type === 'details' ? node : node.children.map(findDetails).find(Boolean)
    const technical = findDetails(page.root)!
    expect(technical).toBeTruthy()
    expect(technical.props?.open).toBeUndefined()
    expect(textOf(technical)).toContain('task-31')
    expect(textOf(technical)).toContain('ALL_TASKS_DONE')
    expect(value.snapshot?.evidence).toBe(JSON.stringify(checks))
    const unavailable = await setup(KEY, {
      processInstanceId: 'foreign-process',
      businessKey: KEY,
      names: { '9': '其他实例人员' }
    })
    expect(textOf(unavailable.root)).toContain('姓名未获取')
    expect(textOf(unavailable.root)).toContain('真实审核意见')
    expect(textOf(unavailable.root)).not.toContain('其他实例人员')
    const hasButton = (node: TestNode): boolean =>
      node.type === 'button' || node.children.some(hasButton)
    expect(hasButton(page.root)).toBe(false)
  })

  it('rejects empty, foreign-prefix and non-positive or out-of-Long business keys without querying', async () => {
    for (const key of [
      '',
      `FOREIGN:${ID}`,
      'PROJECT_NORMAL_CLOSURE:0',
      'PROJECT_NORMAL_CLOSURE:-1',
      'PROJECT_NORMAL_CLOSURE:1.5',
      'PROJECT_NORMAL_CLOSURE:9223372036854775808'
    ]) {
      const page = await setup(key)
      expect(textOf(page.root)).toContain('无效的正常闭环业务 Key')
    }
    expect(getNormalClosureApplication).not.toHaveBeenCalled()
  })
})
