import { createRouter, createWebHistory } from 'vue-router'

import { getUserManager } from '@/auth/oidc'
import { loadRuntimeConfig } from '@/config/runtime'

const ProjectCollectionView = () => import('@/views/ProjectCollectionView.vue')
const OverviewManagementView = () => import('@/views/management/OverviewManagementView.vue')
const ScriptsManagementView = () => import('@/views/management/ScriptsManagementView.vue')
const TasksManagementView = () => import('@/views/management/TasksManagementView.vue')
const RecordsManagementView = () => import('@/views/management/RecordsManagementView.vue')
const SettingsManagementView = () => import('@/views/management/SettingsManagementView.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/overview',
      name: 'overview',
      component: OverviewManagementView,
      meta: { title: '设备运维总览', section: '设备运维管理' }
    },
    {
      path: '/connections',
      name: 'connections',
      redirect: '/projects/direct',
      meta: { title: '连接工作台', section: '设备运维管理' }
    },
    {
      path: '/scripts',
      name: 'scripts',
      component: ScriptsManagementView,
      meta: { title: '脚本管理', section: '设备运维管理' }
    },
    {
      path: '/tasks',
      name: 'tasks',
      component: TasksManagementView,
      meta: { title: '采集任务', section: '设备运维管理' }
    },
    {
      path: '/records',
      name: 'records',
      component: RecordsManagementView,
      meta: { title: '采集记录', section: '设备运维管理' }
    },
    {
      path: '/settings',
      name: 'settings',
      component: SettingsManagementView,
      meta: { title: '平台设置', section: '设备运维管理' }
    },
    {
      path: '/parser',
      name: 'parser',
      component: () => import('@/views/management/ParserManagementView.vue'),
      meta: { title: '解析控制台', section: '设备运维管理' }
    },
    {
      path: '/records/:collectionId',
      name: 'record-detail',
      component: RecordsManagementView,
      meta: { title: '采集记录详情', section: '设备运维管理' }
    },
    {
      path: '/projects/:projectKey',
      name: 'project-collection',
      component: ProjectCollectionView,
      props: true
    },
    {
      path: '/embed/projects/:projectKey',
      name: 'embedded-project-collection',
      component: ProjectCollectionView,
      props: (route) => ({ projectKey: route.params.projectKey, embedded: true })
    },
    {
      path: '/auth/callback',
      name: 'auth-callback',
      component: () => import('@/views/AuthCallbackView.vue')
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/projects/direct'
    }
  ]
})

router.beforeEach(async (to) => {
  if (to.name === 'auth-callback') return true
  if (to.name === 'record-detail' && to.query.collectionId !== to.params.collectionId) {
    return { path: to.path, query: { ...to.query, collectionId: String(to.params.collectionId) }, replace: true }
  }
  const config = await loadRuntimeConfig()
  if (config.authMode === 'local') return true
  const manager = await getUserManager()
  const user = await manager.getUser()
  if (!user || user.expired) {
    await manager.signinRedirect({ state: to.fullPath })
    return false
  }
  return true
})

export default router
