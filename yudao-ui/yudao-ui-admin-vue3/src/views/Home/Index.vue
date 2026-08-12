<template>
  <div>
    <el-card shadow="never">
      <el-skeleton :loading="loading" animated>
        <el-row :gutter="16" justify="space-between">
          <el-col :xl="12" :lg="12" :md="12" :sm="24" :xs="24">
            <div class="flex items-center">
              <el-avatar :src="avatar" :size="70" class="mr-16px">
                <img src="@/assets/imgs/avatar.gif" alt="" />
              </el-avatar>
              <div>
                <div class="text-20px">
                  {{ t('workplace.welcome') }} {{ username }} {{ t('workplace.happyDay') }}
                </div>
                <div class="mt-10px text-14px text-gray-500">
                  {{ t('workplace.toady') }}，欢迎使用 NPMS 项目实施交付管理平台
                </div>
              </div>
            </div>
          </el-col>
          <el-col :xl="12" :lg="12" :md="12" :sm="24" :xs="24">
            <div class="h-70px flex items-center justify-end lt-sm:mt-10px">
              <div class="px-8px text-right">
                <div class="mb-16px text-14px text-gray-400">项目总数</div>
                <CountTo
                  class="text-20px"
                  :start-val="0"
                  :end-val="totalSate.project"
                  :duration="2600"
                />
              </div>
              <el-divider direction="vertical" />
              <div class="px-8px text-right">
                <div class="mb-16px text-14px text-gray-400">待办任务</div>
                <CountTo
                  class="text-20px"
                  :start-val="0"
                  :end-val="totalSate.todo"
                  :duration="2600"
                />
              </div>
              <el-divider direction="vertical" border-style="dashed" />
              <div class="px-8px text-right">
                <div class="mb-16px text-14px text-gray-400">本月访问</div>
                <CountTo
                  class="text-20px"
                  :start-val="0"
                  :end-val="totalSate.access"
                  :duration="2600"
                />
              </div>
            </div>
          </el-col>
        </el-row>
      </el-skeleton>
    </el-card>
  </div>

  <el-row class="mt-8px" :gutter="8" justify="space-between">
    <el-col :xl="16" :lg="16" :md="24" :sm="24" :xs="24" class="mb-8px">
      <el-card shadow="never">
        <template #header>
          <div class="h-3 flex justify-between">
            <span>项目动态</span>
          </div>
        </template>
        <el-skeleton :loading="loading" animated>
          <el-row :gutter="8" class="gap-y-8px">
            <el-col
              v-for="(item, index) in projects"
              :key="`card-${index}`"
              :xl="8"
              :lg="8"
              :md="8"
              :sm="24"
              :xs="24"
              class="!flex"
            >
              <el-card
                shadow="hover"
                class="flex-1 cursor-pointer"
                body-class="flex h-full flex-col"
                @click="handleProjectClick(item.path)"
              >
                <div class="flex items-center">
                  <Icon
                    :icon="item.icon"
                    :size="25"
                    class="mr-8px flex-none"
                    :style="{ color: item.color }"
                  />
                  <span class="min-w-0 line-clamp-2 text-16px" :title="item.name">{{
                    item.name
                  }}</span>
                </div>
                <div
                  class="mt-12px break-all line-clamp-2 text-12px text-gray-400"
                  :title="item.personal"
                >
                  {{ item.personal }}
                </div>
                <div
                  class="mt-auto flex items-center justify-between gap-8px pt-12px text-12px text-gray-400"
                >
                  <span class="min-w-0 truncate" :title="item.status">{{ item.status }}</span>
                  <span class="shrink-0 whitespace-nowrap">
                    {{ formatTime(item.time, 'yyyy-MM-dd') }}
                  </span>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </el-skeleton>
      </el-card>

      <el-card shadow="never" class="mt-8px">
        <el-skeleton :loading="loading" animated>
          <el-row :gutter="20" justify="space-between">
            <el-col :xl="10" :lg="10" :md="24" :sm="24" :xs="24">
              <el-card shadow="hover" class="mb-8px">
                <el-skeleton :loading="loading" animated>
                  <Echart :options="pieOptionsData" :height="280" />
                </el-skeleton>
              </el-card>
            </el-col>
            <el-col :xl="14" :lg="14" :md="24" :sm="24" :xs="24">
              <el-card shadow="hover" class="mb-8px">
                <el-skeleton :loading="loading" animated>
                  <Echart :options="barOptionsData" :height="280" />
                </el-skeleton>
              </el-card>
            </el-col>
          </el-row>
        </el-skeleton>
      </el-card>
    </el-col>
    <el-col :xl="8" :lg="8" :md="24" :sm="24" :xs="24" class="mb-8px">
      <el-card shadow="never">
        <template #header>
          <div class="h-3 flex justify-between">
            <span>快捷入口</span>
          </div>
        </template>
        <el-skeleton :loading="loading" animated>
          <el-row>
            <el-col v-for="item in shortcut" :key="`team-${item.name}`" :span="8" class="mb-8px">
              <div class="flex items-center">
                <Icon :icon="item.icon" class="mr-8px" :style="{ color: item.color }" />
                <el-link type="default" :underline="false" @click="handleShortcutClick(item.url)">
                  {{ item.name }}
                </el-link>
              </div>
            </el-col>
          </el-row>
        </el-skeleton>
      </el-card>
      <el-card shadow="never" class="mt-8px">
        <template #header>
          <div class="h-3 flex justify-between">
            <span>系统公告</span>
          </div>
        </template>
        <el-skeleton :loading="loading" animated>
          <div v-for="(item, index) in notice" :key="`dynamics-${index}`">
            <div class="flex items-center">
              <el-avatar :src="avatar" :size="35" class="mr-16px">
                <img src="@/assets/imgs/avatar.gif" alt="" />
              </el-avatar>
              <div>
                <div class="text-14px">
                  {{ item.type }} : {{ item.title }}
                </div>
                <div class="mt-16px text-12px text-gray-400">
                  {{ formatTime(item.date, 'yyyy-MM-dd') }}
                </div>
              </div>
            </div>
            <el-divider />
          </div>
        </el-skeleton>
      </el-card>
    </el-col>
  </el-row>
</template>
<script lang="ts" setup>
import { set } from 'lodash-es'
import { EChartsOption } from 'echarts'
import { formatTime } from '@/utils'

import { useUserStore } from '@/store/modules/user'
import type { WorkplaceTotal, Project, Notice, Shortcut } from './types'
import { pieOptions, barOptions } from './echarts-data'
import { useRouter } from 'vue-router'

defineOptions({ name: 'Index' })

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(true)
const avatar = userStore.getUser.avatar
const username = userStore.getUser.nickname
const pieOptionsData = reactive<EChartsOption>(pieOptions) as EChartsOption
// 获取统计数
let totalSate = reactive<WorkplaceTotal>({
  project: 0,
  access: 0,
  todo: 0
})

const getCount = async () => {
  const data = {
    project: 40,
    access: 2340,
    todo: 10
  }
  totalSate = Object.assign(totalSate, data)
}

// 获取项目动态
let projects = reactive<Project[]>([])
const getProject = async () => {
  const data = [
    {
      name: '项目组合管理',
      icon: 'simple-icons:springboot',
      personal: '项目组合与项目父子树独立建模',
      status: '进行中',
      time: new Date('2026-07-01'),
      color: '#6DB33F',
      path: '/pms/project-management/governance/portfolio'
    },
    {
      name: '项目主数据',
      icon: 'ep:folder-opened',
      personal: '项目分类、重大项、经理指派',
      status: '进行中',
      time: new Date('2026-07-10'),
      color: '#409EFF',
      path: '/pms/project-management/project'
    },
    {
      name: '任务 WBS',
      icon: 'ep:tickets',
      personal: '工作分解结构与非固定层级',
      status: '规划中',
      time: new Date('2026-07-15'),
      color: '#ff4d4f',
      path: '/pms/project-management/schedule/project-task'
    },
    {
      name: '单机风险',
      icon: 'ep:warning',
      personal: '风险识别、确认、CRM 同步与关闭',
      status: '进行中',
      time: new Date('2026-07-20'),
      color: '#1890ff',
      path: '/pms/engineering/safeguard/eng-risk'
    },
    {
      name: '技术公告',
      icon: 'ep:bell',
      personal: '公告发布、停产停维预检查',
      status: '进行中',
      time: new Date('2026-07-25'),
      color: '#e18525',
      path: '/pms/engineering/safeguard/eng-announcement'
    },
    {
      name: '授权管理',
      icon: 'ep:document-checked',
      personal: '授权提交、审批、回执与终止',
      status: '进行中',
      time: new Date('2026-07-30'),
      color: '#2979ff',
      path: '/pms/engineering/safeguard/eng-authorization'
    }
  ]
  projects = Object.assign(projects, data)
}

// 获取通知公告
let notice = reactive<Notice[]>([])
const getNotice = async () => {
  const data = [
    {
      title: 'NPMS V1 版本已通过验收，38 个 PMS 页面加载正常',
      type: '版本发布',
      keys: ['V1'],
      date: new Date('2026-07-30')
    },
    {
      title: 'V2 工程交付阶段功能开发进行中',
      type: '研发进展',
      keys: ['V2', '工程'],
      date: new Date('2026-07-30')
    },
    {
      title: '后端 58080、前端 18081 端口已固化',
      type: '环境配置',
      keys: ['端口'],
      date: new Date('2026-07-30')
    },
    {
      title: 'Docker 仅承载基础设施，前后端在宿主机运行',
      type: '部署规范',
      keys: ['Docker'],
      date: new Date('2026-07-30')
    }
  ]
  notice = Object.assign(notice, data)
}

// 获取快捷入口
let shortcut = reactive<Shortcut[]>([])

const getShortcut = async () => {
  const data = [
    {
      name: '首页',
      icon: 'ion:home-outline',
      url: '/',
      color: '#1fdaca'
    },
    {
      name: '项目主数据',
      icon: 'ep:folder-opened',
      url: '/pms/project-management/project',
      color: '#ff6b6b'
    },
    {
      name: '单机风险',
      icon: 'ep:warning',
      url: '/pms/engineering/safeguard/eng-risk',
      color: '#7c3aed'
    },
    {
      name: '技术公告',
      icon: 'ep:bell',
      url: '/pms/engineering/safeguard/eng-announcement',
      color: '#3fb27f'
    },
    {
      name: '授权管理',
      icon: 'ep:document-checked',
      url: '/pms/engineering/safeguard/eng-authorization',
      color: '#4daf1bc9'
    },
    {
      name: '公告预检查',
      icon: 'ep:document',
      url: '/pms/engineering/safeguard/eng-announcement-check',
      color: '#1a73e8'
    }
  ]
  shortcut = Object.assign(shortcut, data)
}

// 用户来源
const getUserAccessSource = async () => {
  const data = [
    { value: 335, name: 'analysis.directAccess' },
    { value: 310, name: 'analysis.mailMarketing' },
    { value: 234, name: 'analysis.allianceAdvertising' },
    { value: 135, name: 'analysis.videoAdvertising' },
    { value: 1548, name: 'analysis.searchEngines' }
  ]
  set(
    pieOptionsData,
    'legend.data',
    data.map((v) => t(v.name))
  )
  pieOptionsData!.series![0].data = data.map((v) => {
    return {
      name: t(v.name),
      value: v.value
    }
  })
}
const barOptionsData = reactive<EChartsOption>(barOptions) as EChartsOption

// 周活跃量
const getWeeklyUserActivity = async () => {
  const data = [
    { value: 13253, name: 'analysis.monday' },
    { value: 34235, name: 'analysis.tuesday' },
    { value: 26321, name: 'analysis.wednesday' },
    { value: 12340, name: 'analysis.thursday' },
    { value: 24643, name: 'analysis.friday' },
    { value: 1322, name: 'analysis.saturday' },
    { value: 1324, name: 'analysis.sunday' }
  ]
  set(
    barOptionsData,
    'xAxis.data',
    data.map((v) => t(v.name))
  )
  set(barOptionsData, 'series', [
    {
      name: t('analysis.activeQuantity'),
      data: data.map((v) => v.value),
      type: 'bar'
    }
  ])
}

const getAllApi = async () => {
  await Promise.all([
    getCount(),
    getProject(),
    getNotice(),
    getShortcut(),
    getUserAccessSource(),
    getWeeklyUserActivity()
  ])
  loading.value = false
}

const handleProjectClick = (path: string) => {
  router.push(path)
}

const handleShortcutClick = (url: string) => {
  router.push(url)
}

getAllApi()
</script>
