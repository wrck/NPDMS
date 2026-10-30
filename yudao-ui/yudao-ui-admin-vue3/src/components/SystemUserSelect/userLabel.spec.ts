import { expect, it } from 'vitest'
import { formatSystemUserLabel } from './userLabel'

it('formats known accounts without duplicating historical account-name values', () => {
  expect(formatSystemUserLabel({ username: 'k06057', nickname: '康志伟' })).toBe('k06057-康志伟')
  expect(formatSystemUserLabel({ username: 'k06057', nickname: 'k06057-康志伟' })).toBe('k06057-康志伟')
  expect(formatSystemUserLabel({ nickname: '历史姓名' })).toBe('历史姓名')
  expect(formatSystemUserLabel({ username: 'k06057' })).toBe('k06057')
})
