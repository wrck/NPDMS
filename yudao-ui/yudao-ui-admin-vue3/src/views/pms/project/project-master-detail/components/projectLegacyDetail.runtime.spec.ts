// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElPagination } from 'element-plus'
import { defineComponent, h } from 'vue'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import Panel from './ProjectLegacyDetailPanel.vue'
import {
  getLegacySources,
  getLegacyRecords,
  getLegacyNativeReport,
  getLegacyNativeRecordStatus,
  type LegacyDomain,
  type LegacySource
} from '@/api/pms/project/legacy-detail'

vi.mock('@/config/axios', () => ({ default: {} }))
vi.mock('@/api/pms/project/legacy-detail', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/pms/project/legacy-detail')>()),
  getLegacySources: vi.fn(),
  getLegacyRecords: vi.fn(),
  getLegacyNativeReport: vi.fn(),
  getLegacyNativeRecordStatus: vi.fn()
}))
const domains: LegacyDomain[] = [
  {
    code: 'project',
    label: '原项目',
    group: '项目与人员',
    sourceTable: 'pm_project_header',
    keyField: 'projectId',
    fields: [
      { key: 'projectId', label: '原项目编号' },
      { key: 'projectName', label: '原项目名称' }
    ]
  },
  {
    code: 'orders',
    label: '来源销售订单',
    group: '商务资料',
    sourceTable: 'pm_order_data_from_erp',
    keyField: 'id',
    fields: [{ key: 'orderNumber', label: '订单号' }]
  },
  {
    code: 'order-lines',
    label: '来源订单行',
    group: '商务资料',
    sourceTable: 'pm_order_line_from_erp',
    keyField: 'id',
    fields: [{ key: 'itemCode', label: '产品编码' }]
  }
]
const source: LegacySource = {
  id: '999999999999999901',
  sourceSystem: 'DPPMS',
  sourceProjectKey: '101',
  sourceContractNo: 'SYNTHETIC-CONTRACT',
  snapshotId: '999999999999999902',
  checksum: 'synthetic',
  sourceReadAt: '2026-10-08T10:00:00',
  capturedAt: '',
  counts: { project: 1, orders: 1, 'order-lines': 1 },
  domains,
  deferredDomains: ['旧交付件及附件：用户明确暂不同步']
}
const order = {
  id: '999999999999999903',
  domain: 'orders',
  sourceKey: '11',
  sourceTable: 'pm_order_data_from_erp',
  checksum: '',
  values: { orderNumber: 'SYNTHETIC-ORDER' },
  parentDomain: 'project',
  parentSourceKey: '101',
  redactedFields: []
}
let wrapper: ReturnType<typeof mount> | undefined
beforeEach(() => {
  vi.mocked(getLegacyNativeReport).mockReset().mockResolvedValue({
    sourceId: source.id,
    snapshotId: source.snapshotId,
    sourceCount: 3,
    mappedCount: 0,
    retainedCount: 0,
    pendingCount: 3,
    domains: []
  })
  vi.mocked(getLegacyNativeRecordStatus).mockReset().mockResolvedValue({})
  vi.mocked(getLegacySources).mockReset().mockResolvedValue([source])
  vi.mocked(getLegacyRecords)
    .mockReset()
    .mockResolvedValue({ list: [order], total: 1 })
})

it('shows retained reasons and the native business targets without changing historical fields', async () => {
  vi.mocked(getLegacyNativeReport).mockResolvedValue({
    sourceId: source.id,
    snapshotId: source.snapshotId,
    sourceCount: 3,
    mappedCount: 1,
    retainedCount: 2,
    pendingCount: 0,
    domains: []
  })
  vi.mocked(getLegacyNativeRecordStatus).mockResolvedValue({
    '11': {
      classification: 'MAPPED',
      targets: [
        {
          targetContext: 'COM',
          targetObjectType: 'SalesOrder',
          targetTable: 'com_sales_order',
          targetId: '123',
          targetRole: 'ORDER',
          targetSequence: 0
        }
      ]
    }
  })
  wrapper = render()
  await flushPromises()
  expect(wrapper.text()).toContain('已有目标记录 1 条')
  expect(wrapper.text()).toContain('保留来源资料 2 条')
  await wrapper
    .findAll('button')
    .find((b) => b.text() === '商务资料')!
    .trigger('click')
  await flushPromises()
  expect(wrapper.text()).toContain('SYNTHETIC-ORDER')
  const action = wrapper.findAll('button').find((b) => b.text().includes('查看详情'))!
  await action.trigger('click')
  await flushPromises()
  expect(document.body.textContent).toContain('已关联：销售订单')
})

it('does not report a completed migration when loading native evidence fails', async () => {
  vi.mocked(getLegacyNativeRecordStatus).mockRejectedValue(new Error('Evidence unavailable'))
  wrapper = render()
  await flushPromises()
  expect(wrapper.text()).toContain('业务承接状态暂不可用')
  expect(wrapper.text()).toContain('状态暂不可用')
  expect(wrapper.text()).not.toContain('已有目标记录 1 条')
  await wrapper
    .findAll('button')
    .find((b) => b.text() === '商务资料')!
    .trigger('click')
  await flushPromises()
  expect(wrapper.text()).toContain('SYNTHETIC-ORDER')
})
afterEach(() => {
  wrapper?.unmount()
  document.body.innerHTML = ''
  wrapper = undefined
})
// Preserve the shared Pagination contract while exercising Element Plus's real page clamping.
const PaginationHarness = defineComponent({
  props: { page: Number, limit: Number, total: Number },
  emits: ['update:page'],
  setup:
    (props, { emit }) =>
    () =>
      h(ElPagination, {
        currentPage: props.page,
        pageSize: props.limit,
        total: props.total,
        layout: 'prev, pager, next',
        'onUpdate:currentPage': (page: number) => emit('update:page', page)
      })
})
const render = (realPagination = false) =>
  (wrapper = mount(Panel, {
    props: { projectId: '90001' },
    attachTo: document.body,
    global: {
      plugins: [ElementPlus],
      stubs: {
        ContentWrap: { template: '<section><slot /></section>' },
        Pagination: realPagination ? PaginationHarness : true
      }
    }
  }))

it('uses precise string IDs and a frozen snapshot in each records query', async () => {
  render()
  await flushPromises()
  expect(getLegacyRecords).toHaveBeenCalledWith(
    '90001',
    expect.objectContaining({
      sourceId: source.id,
      snapshotId: source.snapshotId,
      domain: 'project'
    })
  )
  expect(wrapper!.text()).toContain(source.deferredDomains[0])
  await wrapper!
    .findAll('button')
    .find((button) => button.text() === '商务资料')!
    .trigger('click')
  await flushPromises()
  expect(getLegacyRecords).toHaveBeenLastCalledWith(
    '90001',
    expect.objectContaining({ domain: 'orders', pageNo: 1 })
  )
  expect(wrapper!.text()).toContain('SYNTHETIC-ORDER')
})

it('keeps page two selected while its records request is still loading', async () => {
  let finish!: (value: { list: (typeof order)[]; total: number }) => void
  const secondPage = new Promise<{ list: (typeof order)[]; total: number }>((resolve) => {
    finish = resolve
  })
  vi.mocked(getLegacyRecords).mockImplementation((_projectId, query) => {
    if (query.domain !== 'orders') return Promise.resolve({ list: [], total: 0 })
    return query.pageNo === 2 ? secondPage : Promise.resolve({ list: [order], total: 38 })
  })
  render(true)
  await flushPromises()
  await wrapper!
    .findAll('button')
    .find((button) => button.text() === '商务资料')!
    .trigger('click')
  await flushPromises()
  await wrapper!.find('button.btn-next').trigger('click')
  await flushPromises()
  expect(getLegacyRecords).toHaveBeenLastCalledWith(
    '90001',
    expect.objectContaining({ domain: 'orders', pageNo: 2 })
  )
  expect(wrapper!.text()).toContain('共 38 条')
  finish({
    list: [{ ...order, sourceKey: '12', values: { orderNumber: 'SYNTHETIC-PAGE-2' } }],
    total: 38
  })
  await flushPromises()
  expect(wrapper!.text()).toContain('SYNTHETIC-PAGE-2')
})

it('ignores a late source response for the previous project', async () => {
  let finish!: (value: LegacySource[]) => void
  vi.mocked(getLegacySources)
    .mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve
        })
    )
    .mockResolvedValueOnce([{ ...source, sourceProjectKey: '202' }])
  render()
  await wrapper!.setProps({ projectId: '90002' })
  await flushPromises()
  finish([{ ...source, sourceProjectKey: '101' }])
  await flushPromises()
  expect(wrapper!.text()).toContain('原项目 202')
  expect(wrapper!.text()).not.toContain('原项目 101')
})

it('distinguishes an import that has not happened from a failed request and retries', async () => {
  vi.mocked(getLegacySources)
    .mockRejectedValueOnce(new Error('synthetic outage'))
    .mockResolvedValueOnce([])
  render()
  await flushPromises()
  expect(wrapper!.text()).toContain('关联资料加载失败')
  expect(wrapper!.text()).not.toContain('尚未同步')
  await wrapper!
    .findAll('button')
    .find((button) => button.text() === '刷新资料')!
    .trigger('click')
  await flushPromises()
  expect(wrapper!.text()).toContain('尚未同步 DPPMS 关联资料')
})

it('opens all fields and follows the selected order to its exact child rows', async () => {
  render()
  await flushPromises()
  await wrapper!
    .findAll('button')
    .find((button) => button.text() === '商务资料')!
    .trigger('click')
  await flushPromises()
  await wrapper!
    .findAll('button')
    .find((button) => button.text() === '查看详情')!
    .trigger('click')
  await flushPromises()
  expect(document.body.textContent).toContain('订单号')
  const child = [...document.querySelectorAll('button')].find(
    (button) => button.textContent?.trim() === '来源订单行'
  )!
  child.click()
  await flushPromises()
  expect(getLegacyRecords).toHaveBeenLastCalledWith(
    '90001',
    expect.objectContaining({
      domain: 'order-lines',
      parentDomain: 'orders',
      parentKey: '11',
      snapshotId: source.snapshotId
    })
  )
  await wrapper!
    .findAll('button')
    .find((button) => button.text() === '显示此类全部记录')!
    .trigger('click')
  await flushPromises()
  expect(getLegacyRecords).toHaveBeenLastCalledWith(
    '90001',
    expect.not.objectContaining({ parentKey: '11' })
  )
})

it('resolves an answer to its historical question inside the same snapshot', async () => {
  const questionnaireDomains: LegacyDomain[] = [
    {
      code: 'questionnaire-answers',
      label: '历史问卷答案',
      group: '回访与审批',
      sourceTable: 'pm_cl_quesnaire_result_line',
      keyField: 'id',
      fields: [{ key: 'quesnaireTemplateLineId', label: '原题目编号' }]
    },
    {
      code: 'questionnaire-questions',
      label: '历史问卷题目',
      group: '回访与审批',
      sourceTable: 'pm_cl_quesnaire_template_line',
      keyField: 'id',
      fields: [{ key: 'questionContent', label: '题目内容' }]
    }
  ]
  vi.mocked(getLegacySources).mockResolvedValue([
    { ...source, domains: [...domains, ...questionnaireDomains] }
  ])
  vi.mocked(getLegacyRecords).mockResolvedValue({
    list: [
      {
        ...order,
        domain: 'questionnaire-answers',
        sourceKey: '88',
        values: { quesnaireTemplateLineId: '77' }
      }
    ],
    total: 1
  })
  render()
  await flushPromises()
  await wrapper!
    .findAll('button')
    .find((button) => button.text() === '回访与审批')!
    .trigger('click')
  await flushPromises()
  await wrapper!
    .findAll('button')
    .find((button) => button.text() === '查看详情')!
    .trigger('click')
  await flushPromises()
  const question = [...document.querySelectorAll('button')].find(
    (button) => button.textContent?.trim() === '查看题目'
  )!
  question.click()
  await flushPromises()
  expect(getLegacyRecords).toHaveBeenLastCalledWith(
    '90001',
    expect.objectContaining({
      domain: 'questionnaire-questions',
      sourceKey: '77',
      snapshotId: source.snapshotId
    })
  )
})
