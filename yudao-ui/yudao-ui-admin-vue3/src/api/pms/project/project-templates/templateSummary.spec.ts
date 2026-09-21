import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/config/axios'
import * as Template from './index'

vi.mock('@/config/axios', () => ({ default: { get: vi.fn() } }))

const draft = (): Template.TemplateDesignerDocument => ({
  ...Template.emptyDesignerDocument(),
  match: { signingMethod: 'DIRECT' },
  stages: [
    { nodeKey: 'stage:S0', code: 'S0', name: '立项', start: true, terminal: false, workBinding: { type: 'STAGE_NATIVE' }, permission: {} },
    { nodeKey: 'stage:S4', code: 'S4', name: '实施', start: false, terminal: true, workBinding: { type: 'STAGE_NATIVE' }, permission: {} }
  ],
  tasks: [
    { nodeKey: 'task:S0', code: 'S0_TASK', name: '立项任务', stageCode: 'S0', workBinding: { type: 'TASK_NATIVE' }, permission: {} },
    { nodeKey: 'task:S4', code: 'S4_TASK', name: '实施任务', stageCode: 'S4', workBinding: { type: 'TASK_NATIVE' }, permission: {} }
  ]
})

const publishedContent = (): Template.TemplateDefinitionContent => ({
  signingMethod: 'DIRECT',
  stages: [
    { stageCode: 'S0', name: '立项', start: true },
    { stageCode: 'S4', name: '实施', terminal: true }
  ],
  tasks: [
    { taskCode: 'S0_TASK', name: '立项任务', stageCode: 'S0' },
    { taskCode: 'S4_TASK', name: '实施任务', stageCode: 'S4' }
  ],
  milestones: [],
  deliverables: [],
  gates: []
})

beforeEach(() => vi.clearAllMocks())

describe('project template list summary', () => {
  it('uses the existing draft when a draft revision exists', async () => {
    vi.mocked(request.get).mockImplementation(({ url }: { url: string }) => {
      if (url === '/api/v1/pms/project-templates/4') return Promise.resolve({ revisions: [{ revisionNo: 0, status: 'DRAFT' }] })
      if (url === '/api/v1/pms/project-templates/4/draft') return Promise.resolve(draft())
      throw new Error(`unexpected ${url}`)
    })

    await expect(Template.getProjectTemplateSummary(4)).resolves.toMatchObject({
      match: { signingMethod: 'DIRECT' }, stageCount: 2, taskCount: 1
    })
    expect(request.get).toHaveBeenCalledTimes(2)
  })

  it('uses the newest published revision when a historical template has no draft', async () => {
    vi.mocked(request.get).mockImplementation(({ url }: { url: string }) => {
      if (url === '/api/v1/pms/project-templates/4') return Promise.resolve({ revisions: [
        { revisionNo: 2, status: 'PUBLISHED' }, { revisionNo: 5, status: 'PUBLISHED' }
      ] })
      if (url === '/api/v1/pms/project-templates/4/revisions/5') return Promise.resolve({ content: publishedContent() })
      throw new Error(`unexpected ${url}`)
    })

    await expect(Template.getProjectTemplateSummary(4)).resolves.toMatchObject({ stageCount: 2, taskCount: 1 })
    expect(request.get).toHaveBeenLastCalledWith({ url: '/api/v1/pms/project-templates/4/revisions/5' })
  })

  it('returns no summary when the template has no revision', async () => {
    vi.mocked(request.get).mockResolvedValue({ revisions: [] })

    await expect(Template.getProjectTemplateSummary(4)).resolves.toBeUndefined()
    expect(request.get).toHaveBeenCalledTimes(1)
  })

  it('propagates a read failure instead of inventing a summary', async () => {
    vi.mocked(request.get).mockRejectedValue(new Error('read failed'))

    await expect(Template.getProjectTemplateSummary(4)).rejects.toThrow('read failed')
    expect(request.get).toHaveBeenCalledTimes(1)
  })
})
