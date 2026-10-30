<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import AppShell from '@/components/AppShell.vue'

const route = useRoute()

const pageMeta = computed(() => ({
  title: typeof route.meta.title === 'string' ? route.meta.title : '管理入口',
  section: typeof route.meta.section === 'string' ? route.meta.section : '设备运维管理'
}))

const capabilities = [
  {
    name: '项目与设备主数据',
    status: '集成侧代理',
    detail: '项目和设备主数据由集成侧代理提供，本页面不直接维护主数据。'
  },
  {
    name: '管理持久化接口',
    status: '未提供',
    detail: '当前入口不声明新增、编辑、删除等管理持久化 API 已实现。'
  },
  {
    name: '采集工作台',
    status: '可从连接入口进入',
    detail: '连接入口会转到指定项目上下文，后续操作以项目范围为准。'
  }
]
</script>

<template>
  <AppShell
    :title="pageMeta.title"
    subtitle="管理导航已就位；各项能力的可用性以集成侧代理和后续工作台交付为准。"
  >
    <template #context>
      <p class="management-placeholder__section">{{ pageMeta.section }}</p>
    </template>

    <section class="management-placeholder" aria-labelledby="management-capability-title">
      <header>
        <p class="management-placeholder__eyebrow">CAPABILITY STATUS</p>
        <h2 id="management-capability-title">能力状态</h2>
        <p>
          项目和设备主数据来自集成侧代理。此管理入口用于明确导航与能力边界，不表示不可用的持久化接口已经实现。
        </p>
      </header>

      <ul class="management-placeholder__list" aria-label="能力状态列表">
        <li v-for="capability in capabilities" :key="capability.name">
          <div>
            <h3>{{ capability.name }}</h3>
            <p>{{ capability.detail }}</p>
          </div>
          <span class="management-placeholder__status">{{ capability.status }}</span>
        </li>
      </ul>
    </section>
  </AppShell>
</template>

<style scoped>
.management-placeholder__section {
  margin: 0;
  color: var(--slate);
  font-family: "Cascadia Code", "JetBrains Mono", monospace;
  font-size: 0.75rem;
}

.management-placeholder {
  max-width: 64rem;
  padding: 1.25rem;
  border-top: 4px solid var(--signal);
  background: var(--surface);
}

.management-placeholder header > p {
  max-width: 48rem;
  margin-bottom: 0;
  color: var(--slate);
  line-height: 1.7;
}

.management-placeholder__eyebrow {
  margin-top: 0;
  color: var(--signal) !important;
  font-family: "Cascadia Code", "JetBrains Mono", monospace;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.management-placeholder h2 {
  margin: 0.25rem 0 0.75rem;
  font-size: 1.25rem;
}

.management-placeholder__list {
  display: grid;
  gap: 1px;
  margin: 1.25rem 0 0;
  padding: 0;
  border: 1px solid var(--line);
  background: var(--line);
  list-style: none;
}

.management-placeholder__list li {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem;
  background: var(--surface);
}

.management-placeholder__list h3,
.management-placeholder__list p {
  margin-top: 0;
}

.management-placeholder__list h3 {
  margin-bottom: 0.375rem;
  font-size: 1rem;
}

.management-placeholder__list p {
  margin-bottom: 0;
  color: var(--slate);
  line-height: 1.6;
}

.management-placeholder__status {
  flex: 0 0 auto;
  padding: 0.25rem 0.5rem;
  border: 1px solid var(--signal);
  color: var(--signal);
  font-family: "Cascadia Code", "JetBrains Mono", monospace;
  font-size: 0.75rem;
  font-weight: 700;
}

@media (max-width: 767px) {
  .management-placeholder__list li {
    flex-direction: column;
  }
}
</style>
