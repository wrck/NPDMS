<script setup lang="ts">
import { computed, useSlots } from 'vue'
import { useRoute } from 'vue-router'

interface NavItem {
  label: string
  to: string
  icon: string
}

const props = defineProps<{
  embedded?: boolean
  elementLayout?: boolean
  title: string
  subtitle: string
}>()

const slots = useSlots()
const route = useRoute()

const navigation: NavItem[] = [
  { label: '总览', to: '/overview', icon: 'OV' },
  { label: '连接', to: '/connections', icon: 'CN' },
  { label: '脚本', to: '/scripts', icon: 'SC' },
  { label: '任务', to: '/tasks', icon: 'TK' },
  { label: '记录', to: '/records', icon: 'RC' },
  { label: '解析', to: '/parser', icon: 'PR' },
  { label: '设置', to: '/settings', icon: 'ST' }
]

function isNavigationActive(item: NavItem): boolean {
  if (item.to === '/connections') {
    return (
      route.path === item.to ||
      route.name === 'project-collection' ||
      route.path.startsWith('/projects/')
    )
  }
  return route.path === item.to || route.path.startsWith(`${item.to}/`)
}

const activeNavigation = computed(() => navigation.find(isNavigationActive)?.to ?? '')
</script>

<template>
  <el-container v-if="props.elementLayout && !embedded" class="app-shell app-shell--element">
    <el-aside class="app-shell__navigation" width="var(--app-shell-element-aside-width)">
      <RouterLink class="app-shell__brand" to="/overview" aria-label="设备运维采集平台总览">
        <span class="app-shell__brand-mark" aria-hidden="true">DO</span>
        <span>
          <strong>DEVICE OPS</strong>
          <small>设备运维采集平台</small>
        </span>
      </RouterLink>

      <nav aria-label="全局导航">
        <el-menu class="app-shell__element-menu" :default-active="activeNavigation" router>
          <el-menu-item
            v-for="item in navigation"
            :key="item.to"
            :index="item.to"
            :aria-current="isNavigationActive(item) ? 'page' : undefined"
          >
            <span class="app-shell__nav-icon" aria-hidden="true">{{ item.icon }}</span>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </el-menu>
      </nav>

      <button
        class="app-shell__terminal-placeholder"
        type="button"
        disabled
        aria-disabled="true"
        title="交互式终端暂未开放"
      >
        交互式终端（暂未开放）
      </button>
    </el-aside>

    <el-container direction="vertical" class="app-shell__workspace">
      <el-header class="app-shell__header" height="auto">
        <div>
          <h1>{{ title }}</h1>
          <p class="app-shell__subtitle">{{ subtitle }}</p>
        </div>
        <div v-if="slots.context" class="app-shell__context">
          <slot name="context" />
        </div>
      </el-header>

      <section v-if="slots.sessions" class="app-shell__sessions" aria-label="会话信息">
        <slot name="sessions" />
      </section>

      <el-main class="app-shell__content">
        <slot />
      </el-main>
    </el-container>
  </el-container>

  <div
    v-else
    class="app-shell"
    :class="{
      'app-shell--embedded': embedded,
      'app-shell--with-sessions': slots.sessions,
      'app-shell--without-sessions': !slots.sessions
    }"
  >
    <aside v-if="!embedded" class="app-shell__navigation">
      <RouterLink class="app-shell__brand" to="/overview" aria-label="设备运维采集平台总览">
        <span class="app-shell__brand-mark" aria-hidden="true">DO</span>
        <span>
          <strong>DEVICE OPS</strong>
          <small>设备运维采集平台</small>
        </span>
      </RouterLink>

      <nav aria-label="全局导航">
        <RouterLink
          v-for="item in navigation"
          :key="item.to"
          :to="item.to"
          class="app-shell__nav-item"
          :class="{ 'app-shell__nav-item--active': isNavigationActive(item) }"
          :aria-current="isNavigationActive(item) ? 'page' : undefined"
        >
          <span class="app-shell__nav-icon" aria-hidden="true">{{ item.icon }}</span>
          {{ item.label }}
        </RouterLink>
      </nav>

      <button
        class="app-shell__terminal-placeholder"
        type="button"
        disabled
        aria-disabled="true"
        title="交互式终端暂未开放"
      >
        交互式终端（暂未开放）
      </button>
    </aside>

    <div class="app-shell__workspace">
      <header class="app-shell__header">
        <div>
          <h1>{{ title }}</h1>
          <p class="app-shell__subtitle">{{ subtitle }}</p>
        </div>
        <div v-if="slots.context" class="app-shell__context">
          <slot name="context" />
        </div>
      </header>

      <section v-if="slots.sessions" class="app-shell__sessions" aria-label="会话信息">
        <slot name="sessions" />
      </section>

      <main class="app-shell__content">
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  display: grid;
  width: 100%;
  height: 100dvh;
  min-height: 0;
  grid-template-columns: 11rem minmax(0, 1fr);
  overflow: hidden;
  background: var(--paper);
}

.app-shell--embedded {
  display: block;
  height: auto;
  min-height: 0;
  overflow: visible;
}

.app-shell__navigation {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: 0.875rem;
  overflow: hidden;
  padding: 0.875rem 0.625rem;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
}

.app-shell__brand {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  min-height: 2.75rem;
  padding: 0 0.375rem 0.75rem;
  border-bottom: 1px solid rgb(255 255 255 / 8%);
  color: inherit;
  text-decoration: none;
}

.app-shell__brand-mark {
  display: grid;
  width: 1.875rem;
  height: 1.875rem;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 0.375rem;
  background: #19a99b;
  color: var(--el-color-primary);
  font-family: "Cascadia Code", "JetBrains Mono", monospace;
  font-size: 0.75rem;
  font-weight: 700;
}

.app-shell__brand strong,
.app-shell__brand small {
  display: block;
}

.app-shell__brand strong {
  font-family: "Cascadia Code", "JetBrains Mono", monospace;
  font-size: 0.75rem;
  letter-spacing: 0.06em;
}

.app-shell__brand small {
  margin-top: 0.125rem;
  color: #789099;
  font-size: 0.75rem;
}

.app-shell__navigation nav {
  display: grid;
  gap: 0.1875rem;
  overflow-y: auto;
}

.app-shell__nav-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-height: 2.25rem;
  padding: 0.5rem 0.625rem;
  border-left: 3px solid transparent;
  border-radius: 0.25rem;
  color: var(--el-text-color-regular);
  font-size: 0.8125rem;
  text-decoration: none;
}

.app-shell__nav-item:hover,
.app-shell__nav-item:focus-visible {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.app-shell__nav-item.router-link-active,
.app-shell__nav-item--active {
  border-left-color: #29b7a7;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 700;
}

.app-shell__nav-icon {
  width: 1.25rem;
  color: #59cfc2;
  font-family: "Cascadia Code", "JetBrains Mono", monospace;
  font-size: 0.75rem;
  font-weight: 700;
  text-align: center;
}

.app-shell__terminal-placeholder {
  width: 100%;
  margin-top: auto;
  padding: 0.5rem 0.625rem;
  border: 1px solid rgb(255 255 255 / 12%);
  border-radius: 0.25rem;
  background: transparent;
  color: #7f969e;
  font-size: 0.75rem;
  text-align: left;
}

.app-shell__terminal-placeholder:disabled {
  cursor: not-allowed;
}

.app-shell__workspace {
  display: grid;
  height: 100%;
  min-width: 0;
  min-height: 0;
  grid-template-rows: 3.875rem minmax(0, 1fr);
  overflow: hidden;
  background: #f5f7f8;
}

.app-shell--with-sessions .app-shell__workspace {
  grid-template-rows: 3.875rem 2.5rem minmax(0, 1fr);
}

.app-shell__header {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0 1rem;
  border-bottom: 1px solid var(--line);
  background: var(--surface);
}

.app-shell__header h1,
.app-shell__header p {
  margin-top: 0;
}

.app-shell__header h1 {
  margin-bottom: 0.125rem;
  font-size: var(--ops-fs-h1);
  font-weight: var(--ops-weight-heading);
  letter-spacing: -0.015em;
}

.app-shell__eyebrow {
  margin-bottom: 0.125rem;
  color: var(--slate);
  font-family: "Cascadia Code", "JetBrains Mono", monospace;
  font-size: 0.75rem;
  letter-spacing: 0.08em;
}

.app-shell__subtitle {
  max-width: 54rem;
  margin-bottom: 0;
  color: var(--slate);
  font-size: 0.75rem;
  font-weight: var(--ops-weight-body);
  line-height: 1.35;
}

.app-shell__context {
  flex: 0 0 auto;
}

.app-shell__sessions {
  min-width: 0;
  min-height: 0;
  padding: 0 0.9375rem;
  overflow: hidden;
  border-bottom: 1px solid var(--line);
  background: #f9fbfb;
}

.app-shell__content {
  display: grid;
  min-width: 0;
  min-height: 0;
  grid-template-rows: minmax(0, 1fr) auto;
  gap: 0.5rem;
  overflow: hidden;
  padding: 0.75rem;
}

.app-shell--embedded .app-shell__workspace {
  display: block;
  height: auto;
  overflow: visible;
}

.app-shell--embedded .app-shell__content {
  display: block;
  overflow: visible;
  padding: 0.5rem 0;
}

.app-shell--embedded .app-shell__header,
.app-shell--embedded .app-shell__sessions {
  min-height: 0;
}

@media (max-width: 767px) {
  .app-shell {
    display: block;
    height: auto;
    min-height: 100dvh;
    overflow: visible;
  }

  .app-shell__navigation {
    gap: 0.75rem;
    overflow: visible;
  }

  .app-shell__navigation nav {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .app-shell__nav-item {
    justify-content: center;
    padding: 0.5rem;
    font-size: 0.8125rem;
  }

  .app-shell__nav-icon {
    display: none;
  }

  .app-shell__terminal-placeholder {
    margin-top: 0;
  }

  .app-shell__workspace {
    display: block;
    height: auto;
    overflow: visible;
  }

  .app-shell__header {
    flex-direction: column;
    align-items: flex-start;
    padding-block: 0.75rem;
  }

  .app-shell__sessions {
    min-height: 2.5rem;
  }

  .app-shell__content {
    display: block;
    overflow: visible;
  }
}

@media (min-width: 768px) and (max-width: 1023px) {
  .app-shell {
    height: auto;
    min-height: 100dvh;
    overflow: visible;
  }

  .app-shell__workspace {
    display: block;
    height: auto;
    overflow: visible;
  }

  .app-shell__content {
    display: block;
    overflow: visible;
  }
}

@media (min-width: 768px) and (max-width: 1199px) {
  .app-shell {
    grid-template-columns: 9rem minmax(0, 1fr);
  }

  .app-shell__brand small {
    display: none;
  }

  .app-shell__subtitle {
    max-width: 34rem;
  }
}

.app-shell--element {
  --app-shell-element-aside-width: 11rem;
  --app-shell-element-header-height: calc(var(--el-component-size-large) * 1.5);
  display: flex;
  height: 100dvh;
  min-height: 0;
  overflow: hidden;
  background: var(--el-bg-color-page);
  color: var(--el-text-color-primary);
}

.app-shell--element .app-shell__navigation {
  gap: calc(var(--el-component-size-small) / 2);
  padding: calc(var(--el-component-size-small) / 2);
  border-right: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
}

.app-shell--element .app-shell__brand {
  gap: calc(var(--el-component-size-small) / 2);
  min-height: var(--el-component-size-large);
  padding: 0 calc(var(--el-component-size-small) / 4) calc(var(--el-component-size-small) / 2);
  border-bottom-color: var(--el-border-color-lighter);
}

.app-shell--element .app-shell__brand-mark {
  width: var(--el-component-size);
  height: var(--el-component-size);
  border-radius: var(--el-border-radius-base);
  background: var(--el-color-primary);
  color: var(--el-color-white);
}

.app-shell--element .app-shell__brand small {
  color: var(--el-text-color-secondary);
}

.app-shell--element .app-shell__navigation nav {
  display: block;
}

.app-shell--element .app-shell__element-menu {
  border-right: 0;
  background: transparent;
}

.app-shell--element :deep(.el-menu-item) {
  gap: calc(var(--el-component-size-small) / 2);
  height: var(--el-component-size);
  padding: 0 calc(var(--el-component-size-small) / 2) !important;
  border-radius: var(--el-border-radius-base);
  color: var(--el-text-color-regular);
  font-size: var(--el-font-size-base);
  line-height: var(--el-component-size);
}

.app-shell--element :deep(.el-menu-item:hover),
.app-shell--element :deep(.el-menu-item:focus-visible) {
  background: var(--el-fill-color-light);
  color: var(--el-color-primary);
}

.app-shell--element :deep(.el-menu-item.is-active) {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: var(--el-font-weight-primary);
}

.app-shell--element .app-shell__nav-icon {
  width: var(--el-component-size-small);
  color: currentcolor;
}

.app-shell--element .app-shell__terminal-placeholder {
  padding: calc(var(--el-component-size-small) / 2);
  border-color: var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  color: var(--el-text-color-secondary);
}

.app-shell--element .app-shell__workspace {
  display: flex;
  height: 100%;
  overflow: hidden;
  background: var(--el-bg-color-page);
}

.app-shell--element .app-shell__header {
  min-height: var(--app-shell-element-header-height);
  flex-wrap: wrap;
  padding: 0.75rem calc(var(--el-component-size-large) / 2);
  border-bottom-color: var(--el-border-color);
  background: var(--el-bg-color);
}

.app-shell--element .app-shell__header > :first-child {
  min-width: 0;
}

.app-shell--element .app-shell__context {
  max-width: 100%;
}

.app-shell--element .app-shell__eyebrow,
.app-shell--element .app-shell__subtitle {
  color: var(--el-text-color-secondary);
}

.app-shell--element .app-shell__sessions {
  min-height: var(--el-component-size);
  padding: 0 calc(var(--el-component-size-small) / 2);
  border-bottom-color: var(--el-border-color);
  background: var(--el-fill-color-blank);
}

.app-shell--element .app-shell__content {
  display: block;
  min-height: 0;
  padding: calc(var(--el-component-size-small) / 2);
  overflow-x: hidden;
  overflow-y: auto;
  background: var(--el-bg-color-page);
}

@media (max-width: 767px) {
  .app-shell--element {
    display: flex;
    height: auto;
    min-height: 100dvh;
    flex-direction: column;
    overflow: visible;
  }

  .app-shell--element > .app-shell__navigation {
    width: 100% !important;
    overflow: visible;
  }

  .app-shell--element .app-shell__workspace {
    display: flex;
    height: auto;
    min-height: 0;
    overflow: visible;
  }

  .app-shell--element .app-shell__content {
    display: block;
    overflow: visible;
  }
}

@media (min-width: 768px) and (max-width: 1199px) {
  .app-shell--element,
  .app-shell--element .app-shell__workspace {
    display: flex;
    height: auto;
    min-height: 100dvh;
    overflow: visible;
  }

  .app-shell--element .app-shell__content {
    display: block;
    overflow: visible;
  }
}
</style>
