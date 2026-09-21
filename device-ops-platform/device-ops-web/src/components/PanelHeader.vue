<script setup lang="ts">
interface PanelHeaderProps {
  title: string
  eyebrow?: string
  icon: string
  summary?: string
  collapsible?: boolean
  collapsed?: boolean
}

withDefaults(defineProps<PanelHeaderProps>(), {
  eyebrow: undefined,
  summary: undefined,
  collapsible: false,
  collapsed: false
})

defineEmits<{ toggle: [] }>()
</script>

<template>
  <header class="panel-header">
    <span class="panel-header__icon" aria-hidden="true">{{ icon }}</span>
    <div class="panel-header__content">
      <p v-if="eyebrow" class="panel-header__eyebrow">{{ eyebrow }}</p>
      <h2>{{ title }}</h2>
    </div>
    <p v-if="summary" class="panel-header__summary">{{ summary }}</p>
    <el-button
      v-if="collapsible"
      class="panel-header__toggle"
      size="small"
      text
      :aria-expanded="!collapsed"
      :aria-label="collapsed ? `展开${title}` : `折叠${title}`"
      @click="$emit('toggle')"
    >
      {{ collapsed ? '展开' : '折叠' }}
    </el-button>
  </header>
</template>

<style scoped>
.panel-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-height: 2rem;
  margin-bottom: 0.5rem;
}

.panel-header__icon {
  padding-top: 0.125rem;
  color: var(--signal);
  font-family: var(--ops-font-sans);
  font-size: 1rem;
  line-height: 1;
}

.panel-header__content {
  min-width: 0;
}

.panel-header__eyebrow,
.panel-header h2,
.panel-header__summary {
  margin-top: 0;
}

.panel-header__eyebrow {
  margin-bottom: 0.5rem;
  color: var(--slate);
  font-family: var(--ops-font-sans);
  font-size: 0.75rem;
  letter-spacing: 0.08em;
}

.panel-header h2 {
  margin-bottom: 0;
  font-size: var(--ops-fs-h2);
  font-weight: var(--ops-weight-heading);
}

.panel-header__summary {
  margin: 0.25rem 0 0 auto;
  color: var(--slate);
  font-family: var(--ops-font-sans);
  font-size: 0.75rem;
  overflow-wrap: anywhere;
}

.panel-header__toggle {
  margin-left: auto;
  --el-button-text-color: var(--el-color-primary);
  --el-button-hover-text-color: var(--el-color-primary-light-3);
}

.panel-header__summary + .panel-header__toggle {
  margin-left: 0;
}
</style>
