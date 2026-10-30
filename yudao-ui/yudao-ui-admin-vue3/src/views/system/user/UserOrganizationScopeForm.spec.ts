import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElSelect, ElTreeSelect, ElDatePicker, ElTable } from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import UserOrganizationScopeForm from './UserOrganizationScopeForm.vue'
import type { UserVO } from '@/api/system/user'

const mock = vi.hoisted(() => ({
  canManage: true,
  getScopes: vi.fn(),
  create: vi.fn(),
  revise: vi.fn(),
  disable: vi.fn(),
  getCompanies: vi.fn(),
  getDepartments: vi.fn(),
  message: { success: vi.fn(), warning: vi.fn(), confirm: vi.fn() }
}))
vi.mock('@/api/system/user/organizationScope', () => ({
  getUserOrganizationScopes: mock.getScopes,
  createUserOrganizationScope: mock.create,
  reviseUserOrganizationScope: mock.revise,
  disableUserOrganizationScope: mock.disable
}))
vi.mock('@/api/system/company', () => ({ getSimpleCompanyList: mock.getCompanies }))
vi.mock('@/api/system/dept', () => ({ getSimpleDeptList: mock.getDepartments }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => mock.canManage }))

const user = { id: 1101, username: 'scope-user', nickname: '范围用户' } as UserVO
let wrapper: ReturnType<typeof mount>
const button = (text: string) => wrapper.findAll('button').find((item) => item.text() === text)!
const open = async () => {
  wrapper = mount(UserOrganizationScopeForm, {
    global: {
      plugins: [ElementPlus],
      stubs: {
        Dialog: {
          props: ['modelValue'],
          template: '<section v-if="modelValue"><slot /><slot name="footer" /></section>'
        },
        Pagination: true
      }
    }
  })
  await (wrapper.vm as unknown as { open: (row: UserVO) => Promise<void> }).open(user)
  await flushPromises()
}
const enterScope = async () => {
  await button('新增范围').trigger('click')
  wrapper.find('.el-form-item').findComponent(ElSelect).vm.$emit('update:modelValue', 2101)
  await wrapper.find('input[placeholder^="填写既定范围角色编码"]').setValue('PROJECT_MANAGER')
  wrapper.findAllComponents(ElDatePicker)[0].vm.$emit('update:modelValue', '2026-10-24 10:00:00')
  await flushPromises()
}

beforeEach(() => {
  vi.clearAllMocks()
  mock.canManage = true
  mock.getScopes.mockResolvedValue({ list: [], total: 0 })
  mock.getCompanies.mockResolvedValue([
    { id: 2101, code: 'SCOPE-COMPANY', name: '范围公司', status: 0 }
  ])
  // 精简接口本身只返回启用部门，不带 status。
  mock.getDepartments.mockResolvedValue([
    { id: 3101, code: 'SCOPE-DEPT', name: '范围部门', parentId: 0 }
  ])
  mock.create.mockResolvedValue(1)
  vi.stubGlobal('useMessage', () => mock.message)
})
afterEach(() => {
  wrapper?.unmount()
  vi.unstubAllGlobals()
})

describe('用户公司部门范围维护', () => {
  it('保留无status字段的精简部门，并以平台时间戳提交公司级范围', async () => {
    await open()
    await enterScope()
    expect(wrapper.findComponent(ElTreeSelect).props('data')).toEqual([
      expect.objectContaining({ id: 3101, code: 'SCOPE-DEPT' })
    ])
    await button('保存范围').trigger('click')
    await flushPromises()
    expect(mock.create).toHaveBeenCalledWith(
      expect.objectContaining({
        userId: 1101,
        companyId: 2101,
        departmentId: undefined,
        scopeRole: 'PROJECT_MANAGER',
        effectiveFrom: new Date('2026-10-24T10:00:00').getTime(),
        effectiveTo: undefined
      })
    )
    expect(mock.getScopes).toHaveBeenCalledTimes(2)
  })

  it('只读权限只查询范围，不加载维护选项或显示写按钮', async () => {
    mock.canManage = false
    await open()
    expect(mock.getScopes).toHaveBeenCalledWith(expect.objectContaining({ userId: 1101 }))
    expect(mock.getCompanies).not.toHaveBeenCalled()
    expect(mock.getDepartments).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('新增范围')
    expect(wrapper.text()).not.toContain('保存范围')
  })

  it('到期时间不晚于生效时间时保留表单并阻止提交', async () => {
    await open()
    await enterScope()
    wrapper.findAllComponents(ElDatePicker)[1].vm.$emit('update:modelValue', '2026-10-24 09:00:00')
    await button('保存范围').trigger('click')
    await flushPromises()
    expect(mock.create).not.toHaveBeenCalled()
    expect(mock.message.warning).toHaveBeenCalledWith('到期时间必须晚于生效时间')
    expect(wrapper.text()).toContain('保存范围')
  })

  it('读取失败显示错误，刷新成功后恢复列表', async () => {
    mock.getScopes.mockRejectedValueOnce(new Error('network unavailable'))
    await open()
    expect(wrapper.text()).toContain('范围加载失败，请点击刷新重试。')
    await button('刷新').trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('范围加载失败，请点击刷新重试。')
  })

  it('切换用户时丢弃上一用户迟到的范围响应', async () => {
    await open()
    let resolveOld: (value: unknown) => void = () => {}
    mock.getScopes.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveOld = resolve
        })
    )
    const dialog = wrapper.vm as unknown as { open: (row: UserVO) => Promise<void> }
    const oldRequest = dialog.open(user)
    await dialog.open({ ...user, id: 1102, nickname: '其他用户' })
    resolveOld({ list: [{ id: 99, companyName: '上一用户的范围' }], total: 1 })
    await oldRequest
    await flushPromises()
    expect(wrapper.findComponent(ElTable).props('data')).toEqual([])
    expect(wrapper.text()).not.toContain('上一用户的范围')
  })
})
