import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, defineComponent, h, inject, nextTick, provide } from 'vue'
import Contacts from './index.vue'
import * as ContactsApi from '@/api/pms/customer/contacts'
import * as CustomerApi from '@/api/pms/customer'
import { mount, passthrough, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'

vi.mock('@/api/pms/customer/contacts', () => ({ getMasterPage: vi.fn(), getProjectContext: vi.fn(), getProjectPage: vi.fn() }))
vi.mock('@/api/pms/customer', () => ({ __v_isRef: false, getCustomer: vi.fn(), getCustomerPage: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => false }))
vi.mock('@/utils/dict', () => ({
  getIntDictOptions: () => [],
  getDictLabel: (type: string, value: string) => type === 'pms_customer_contact_role' && value === 'TEST_CUSTOMER_ROLE' ? '验收用客户联络角色' : ''
}))
vi.mock('./ContactFields.vue', () => ({ default: { render: () => null } }))
vi.mock('./ContactHistoryDialog.vue', () => ({ default: { render: () => null } }))

const table = defineComponent({
  props: ['data'], setup(props, { slots }) {
    provide('test-contact-rows', computed(() => props.data))
    return () => h('table', slots.default?.())
  }
})
const column = defineComponent({
  props: ['prop', 'label'], setup(props, { slots }) {
    const rows = inject<any>('test-contact-rows')
    return () => h('section', { 'data-label': props.label }, [props.label, ...rows.value.map((row: any) =>
      h('span', slots.default ? slots.default({ row }) : row[props.prop]))])
  }
})
const components = { ElTable: table, ElTableColumn: column, ElInput: passthrough, ElSelect: passthrough, ElOption: passthrough, ElRadio: passthrough, ElRadioGroup: passthrough, PmsEntitySelect: passthrough, DictTag: passthrough }
const flush = async () => { for (let i = 0; i < 16; i++) await nextTick() }
const page = { list: [
  { id: 1, customerId: 1001, customerName: '验收客户', name: '联系人甲', roleCode: 'TEST_CUSTOMER_ROLE', status: 0 },
  { id: 2, customerId: 1001, customerName: '验收客户', name: '联系人乙', roleCode: '历史角色', status: 0 }
], total: 2 }

describe('contact customer identity and platform role display', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(ContactsApi.getMasterPage).mockResolvedValue(page as any)
    vi.mocked(ContactsApi.getProjectPage).mockResolvedValue(page as any)
    vi.mocked(ContactsApi.getProjectContext).mockResolvedValue({ project: { projectId: 7, customerId: 1001, canManage: false }, customerName: '验收客户' } as any)
    vi.mocked(CustomerApi.getCustomer).mockResolvedValue({ id: 1001, code: 'CUST-001', name: '验收客户' })
  })

  it('shows the CUS business code and resolves a repeated customer only once', async () => {
    const mounted = mount(Contacts, {}, components)
    try {
      await flush()
      expect(textOf(mounted.root)).toContain('客户编号CUST-001CUST-001')
      expect(CustomerApi.getCustomer).toHaveBeenCalledExactlyOnceWith(1001)
      expect(textOf(mounted.root)).not.toContain('客户联系人角色')
    } finally { mounted.app.unmount() }
  })

  it('keeps contacts visible when the code lookup fails, without substituting the name or ID', async () => {
    vi.mocked(CustomerApi.getCustomer).mockRejectedValue(new Error('customer scope unavailable'))
    const mounted = mount(Contacts, {}, components)
    try {
      await flush()
      expect(textOf(mounted.root)).toContain('客户编号编号未获取编号未获取')
      expect(textOf(mounted.root)).toContain('联系人甲')
      expect(textOf(mounted.root)).toContain('部分客户编号未获取')
    } finally { mounted.app.unmount() }
  })

  it('uses the platform customer-contact dictionary and preserves an unmapped historical value', async () => {
    const mounted = mount(Contacts, { projectId: 7 }, components)
    try {
      await flush()
      expect(textOf(mounted.root)).toContain('客户联系人角色验收用客户联络角色历史角色')
    } finally { mounted.app.unmount() }
  })
})
