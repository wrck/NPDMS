import { readFileSync } from 'node:fs'
import { expect, it, vi } from 'vitest'
import { createCustomerSelectedProject } from '@/api/pms/project/customer-selected'
import request from '@/config/axios'

vi.mock('@/config/axios', () => ({ default: { post: vi.fn() } }))
const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')

it('uses the separate canonical creation command and keeps its idempotency key', async () => {
  const body = { projectName: '项目', customerCode: 'C-001', orderOfficeCompanyId: 1, orderOfficeDepartmentId: 2, creationReason: '验证' }
  await createCustomerSelectedProject(body, 'stable-intent')
  expect(request.post).toHaveBeenCalledWith({ url: '/api/v1/pms/projects', data: body, headers: { 'Idempotency-Key': 'stable-intent' } })
})

it('preserves the original pages and uses selectable customer identity only in the new entry', () => {
  const page = read('./index.vue')
  const original = read('../../projects/index.vue')
  expect(original).toContain('v-model="createForm.customerName"')
  expect(page).toContain('value-field="code"')
  expect(page).toContain(':api="getSelectableCustomers"')
  expect(page).not.toContain('v-model="createForm.customerName"')
  expect(page).not.toContain('customerName: createForm.customerName || undefined')
  expect(page).toContain('/pms-inheritance/project-detail')
  expect(page).toContain('createSubmissionIdempotencyState')
  expect(page).toContain('matchTemplates')
  expect(read('../detail/index.vue')).toContain('legacyOwnerId(route.query.projectId)')
})
