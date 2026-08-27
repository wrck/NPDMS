import { computed, createRenderer, defineComponent, h, inject, nextTick, provide, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { resolveFileUploadMode, useFileSlotState } from './useFileSlotState'
import PmsFileVersionDrawer from './PmsFileVersionDrawer.vue'
import * as FileApi from '@/api/pms/platform/file'
import {
  formStateFromChange,
  reconcilePatchResponseLoss
} from '../../views/pms/project/project-master-detail/components/durationChangeFormState'
import type { ConstructionPlanChangeVO } from '@/api/pms/engineering/construction-plan'

const mediaQuery = vi.hoisted(() => ({ narrow: true }))

vi.mock('@vueuse/core', async () => {
  const { ref: vueRef } = await import('vue')
  return { useMediaQuery: () => vueRef(mediaQuery.narrow) }
})
vi.mock('@/api/pms/platform/file', () => ({ getVersions: vi.fn() }))

interface TestNode {
  type: string
  text?: string
  children: TestNode[]
  parent?: TestNode
}

const renderer = createRenderer<TestNode, TestNode>({
  patchProp: () => undefined,
  insert: (child, parent) => {
    child.parent = parent
    parent.children.push(child)
  },
  remove: (child) => {
    if (child.parent) child.parent.children = child.parent.children.filter((item) => item !== child)
  },
  createElement: (type) => ({ type, children: [] }),
  createText: (text) => ({ type: '#text', text, children: [] }),
  createComment: (text) => ({ type: '#comment', text, children: [] }),
  setText: (node, text) => (node.text = text),
  setElementText: (node, text) => (node.children = [{ type: '#text', text, children: [] }]),
  parentNode: (node) => node.parent ?? null,
  nextSibling: () => null,
  querySelector: () => null,
  setScopeId: () => undefined,
  cloneNode: (node) => ({ ...node, children: [...node.children] }),
  insertStaticContent: (content, parent) => {
    const node = { type: '#static', text: content, children: [], parent }
    parent.children.push(node)
    return [node, node]
  }
})

const passthrough = defineComponent({
  setup(_, { slots }) {
    return () => h('section', slots.default?.())
  }
})
const tableRowsKey = Symbol('tableRows')
const table = defineComponent({
  props: { data: { type: Array, default: () => [] } },
  setup(props, { slots }) {
    provide(
      tableRowsKey,
      computed(() => props.data)
    )
    return () => h('section', slots.default?.())
  }
})
const tableColumn = defineComponent({
  setup(_, { slots }) {
    const rows = inject(tableRowsKey, ref<unknown[]>([]))
    return () =>
      h(
        'section',
        rows.value.map((row) => slots.default?.({ row }))
      )
  }
})

const nodeText = (node: TestNode): string =>
  `${node.text || ''}${node.children.map(nodeText).join('')}`

const change = (overrides: Partial<ConstructionPlanChangeVO> = {}): ConstructionPlanChangeVO => ({
  changeId: 41,
  baseRevisionId: 10,
  candidateRevisionId: 11,
  candidateRevision: {
    revisionId: 11,
    revisionNo: 2,
    calculationBasis: 'DATE_RANGE',
    startDate: '2026-08-01',
    endDate: '2026-08-31',
    durationDays: 31,
    createdBy: 1,
    createdAt: '2026-08-27T00:00:00',
    version: 1,
    current: false
  },
  status: 'DRAFT',
  reasonType: 'CUSTOMER_DELAY',
  reasonDetail: '原始说明',
  customerEvidenceRequired: true,
  applicantUserId: 1,
  createdAt: '2026-08-27T00:00:00',
  version: 3,
  ...overrides
})

describe('F-PLT-001 file interaction state', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders PASSED and SKIPPED as distinct user-visible scan facts', async () => {
    vi.mocked(FileApi.getVersions).mockResolvedValue({
      items: [
        {
          id: 1,
          versionNo: 1,
          sha256: 'a'.repeat(64),
          sizeBytes: 1024,
          mediaType: 'application/pdf',
          scanStatus: 'PASSED',
          availabilityStatus: 'AVAILABLE',
          availabilityVersion: 1,
          createdBy: 1,
          createdAt: '2026-08-27T00:00:00'
        },
        {
          id: 2,
          versionNo: 2,
          sha256: 'b'.repeat(64),
          sizeBytes: 2048,
          mediaType: 'application/pdf',
          scanStatus: 'SKIPPED',
          availabilityStatus: 'AVAILABLE',
          availabilityVersion: 1,
          createdBy: 1,
          createdAt: '2026-08-27T00:00:00'
        }
      ],
      hasMore: false
    })
    for (const narrow of [true, false]) {
      mediaQuery.narrow = narrow
      const root: TestNode = { type: 'root', children: [] }
      const app = renderer.createApp(PmsFileVersionDrawer)
      app.provide(Symbol.for('v-scx'), { modules: new Set<string>() })
      for (const name of ['el-drawer', 'el-skeleton', 'el-empty', 'el-tag', 'el-button']) {
        app.component(name, passthrough)
      }
      app.component('ElTable', table)
      app.component('ElTableColumn', tableColumn)
      const drawer = app.mount(root) as unknown as {
        open: (id: number, key: FileApi.FileBusinessKey) => void
      }

      drawer.open(10, {
        ownerContext: 'SOL',
        objectType: 'PREPARATION',
        objectId: '20',
        purposeCode: 'SITE_SURVEY_EVIDENCE',
        referenceKey: 'site-survey'
      })
      await Promise.resolve()
      await nextTick()

      const rendered = nodeText(root)
      expect(rendered).toContain('已执行并通过扫描')
      expect(rendered).toContain('未执行安全扫描（不代表安全）')
      app.unmount()
    }
  })

  it('keeps the stable slot after detach and rebinds it through ADD_VERSION', () => {
    const slot = useFileSlotState()
    slot.detached({
      artifactId: 901,
      versionNo: 2,
      referenceId: 801,
      referenceKey: 'customer-delay',
      factVersion: 7,
      status: 'DETACHED'
    })

    expect(slot.state).toMatchObject({
      artifactId: 901,
      referenceKey: 'customer-delay',
      referenceVersion: 7
    })
    expect(resolveFileUploadMode(slot.state.artifactId)).toBe('ADD_VERSION')
  })

  it('uses the refreshed change version after a committed PATCH response is lost', () => {
    const before = change()
    const local = formStateFromChange(before)
    local.reasonDetail = '尚未落库的补充说明'
    local.customerEvidenceFileId = 901
    local.customerEvidenceFileVersion = 2
    local.customerEvidenceReferenceKey = 'customer-delay'
    const persisted = change({
      version: 4,
      customerEvidenceFileId: 901,
      customerEvidenceFileVersion: 2,
      customerEvidenceReferenceKey: 'customer-delay'
    })

    const recovered = reconcilePatchResponseLoss(local, persisted)

    expect(recovered.current.version).toBe(4)
    expect(recovered.baseline.customerEvidenceFileId).toBe(901)
    expect(recovered.form.reasonDetail).toBe('尚未落库的补充说明')
    expect(recovered.form.customerEvidenceFileId).toBe(recovered.baseline.customerEvidenceFileId)
  })
})
