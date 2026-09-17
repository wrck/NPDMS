<script setup lang="ts">
/**
 * 低代码标签页渲染引擎。
 *
 * <p>根据传入的 {@link TabConfig} 动态渲染 Element Plus el-tabs，支持：</p>
 * <ul>
 *   <li>4 种页面类型：form（LowCodeFormRenderer）/ list（LowCodeListRenderer）/
 *       related-page（LowCodeRelatedPageRenderer）/ custom（iframe 或 router-view）</li>
 *   <li>v-model 绑定当前激活的 tab name</li>
 *   <li>lazy 懒加载、disabled 禁用、closable/addable/editable 顶层属性</li>
 *   <li>icon 图标显示在标签标题前</li>
 *   <li>visible 显示条件表达式（对 contextData 求值）</li>
 *   <li>props 模板变量解析（${route.params.id} / ${row.field} / ${user.userId} 等）</li>
 * </ul>
 *
 * <p>渲染 form/list/related-page 时通过异步加载对应的低代码配置（getFormByCode /
 * getListByCode / getRelatedPageByCode），并嵌入对应的渲染器组件。
 * custom 类型直接渲染 iframe（pageUrl）或 emit navigate 事件由业务层处理。</p>
 *
 * <p>对外暴露 tab-change / navigate / page-loaded 事件，方便业务层介入。</p>
 */
import { computed, ref, watch, defineAsyncComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type TabsPaneContext } from 'element-plus'
import {
  TabPageType,
  TabsType,
  normalizeTabConfig,
  getFormByCode,
  getListByCode,
  getRelatedPageByCode,
  listForms,
  listLists,
  listRelatedPages,
  type FormConfig,
  type ListConfig,
  type LowCodeFormConfig,
  type LowCodeListConfig,
  type LowCodeRelatedPageConfig,
  type RelatedPageConfig,
  type TabConfig,
  type TabItemConfig
} from '@/api/lowcode'
import { useUserStore } from '@/stores/user'

/** 异步加载子页面渲染器，避免循环依赖 */
const LowCodeFormRenderer = defineAsyncComponent(() => import('@/components/LowCodeFormRendererFacade/index.vue'))
const LowCodeListRenderer = defineAsyncComponent(() => import('@/components/LowCodeListRenderer/index.vue'))
const LowCodeRelatedPageRenderer = defineAsyncComponent(() => import('@/components/LowCodeRelatedPageRenderer/index.vue'))

/** Props 定义 */
const props = withDefaults(
  defineProps<{
    /** 标签页配置（解析后的 TabConfig 对象） */
    config: TabConfig
    /** 当前激活的 tab name（v-model） */
    modelValue?: string
    /** 上下文数据（用于 props 解析与 visible 表达式求值） */
    contextData?: Record<string, unknown>
    /** 设计器预览使用的子页面配置（pageCode → config） */
    previewConfigs?: Record<string, unknown>
    /** 设计器预览时允许引用尚未发布的草稿配置 */
    allowDraft?: boolean
  }>(),
  {
    modelValue: '',
    contextData: () => ({}),
    previewConfigs: () => ({}),
    allowDraft: false
  }
)

/** Emits 定义 */
const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'tab-change', tab: TabItemConfig): void
  (e: 'navigate', url: string, tab: TabItemConfig): void
  (e: 'page-loaded', tab: TabItemConfig, pageConfig: unknown): void
}>()

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 内部维护的激活 tab name */
const activeName = ref<string>(props.modelValue || '')

watch(
  () => props.modelValue,
  (val) => {
    if (val && val !== activeName.value) {
      activeName.value = val
    }
  }
)

const tabsType = computed(() => {
  const t = props.config.type || TabsType.BORDER_CARD
  return t === TabsType.PLAIN ? '' : t
})

const visibleTabs = computed<TabItemConfig[]>(() => {
  return normalizeTabConfig(props.config).tabs.filter((tab) => evalVisible(tab))
})

watch(
  visibleTabs,
  (tabs) => {
    if (tabs.length === 0) {
      activeName.value = ''
      return
    }
    const exists = tabs.some((t) => t.name === activeName.value)
    if (!exists) {
      activeName.value = tabs[0].name
      emit('update:modelValue', activeName.value)
    }
  },
  { immediate: true }
)

function evalVisible(tab: TabItemConfig): boolean {
  const expr = tab.visible
  if (!expr || !expr.trim()) return true
  try {
    const row = (props.contextData.row as Record<string, unknown>) || {}
    const context = (props.contextData.context as Record<string, unknown>) || {}
    // eslint-disable-next-line no-new-func
    const fn = new Function('row', 'context', 'route', 'user', `"use strict"; return (${expr});`)
    const result = fn(row, context, route, userStore.userInfo || {})
    return !!result
  } catch (e) {
    console.warn(`[LowCodeTabRenderer] visible 表达式求值失败: ${expr}`, e)
    return true
  }
}

function resolveProps(tab: TabItemConfig): Record<string, unknown> {
  const result: Record<string, unknown> = {}
  const src = tab.props || {}
  const row = (props.contextData.row as Record<string, unknown>) || {}
  const context = (props.contextData.context as Record<string, unknown>) || {}
  const user = (userStore.userInfo as Record<string, unknown>) || {}
  for (const key of Object.keys(src)) {
    const val = src[key]
    if (typeof val === 'string') {
      result[key] = resolveTemplate(val, { row, context, route, user })
    } else {
      result[key] = val
    }
  }
  return result
}

const TEMPLATE_RE = /\$\{\s*([^}]+?)\s*\}/g

function resolveTemplate(tpl: string, ctx: Record<string, unknown>): string {
  return tpl.replace(TEMPLATE_RE, (_, expr: string) => {
    try {
      // eslint-disable-next-line no-new-func
      const fn = new Function(...Object.keys(ctx), `"use strict"; return (${expr});`)
      const val = fn(...Object.values(ctx))
      return val == null ? '' : String(val)
    } catch (e) {
      console.warn(`[LowCodeTabRenderer] props 模板解析失败: ${expr}`, e)
      return ''
    }
  })
}

const pageConfigCache = ref<Record<string, unknown>>({})
const loadingMap = ref<Record<string, boolean>>({})
const errorMap = ref<Record<string, string>>({})

async function loadPageConfig(tab: TabItemConfig): Promise<void> {
  if (!tab.pageCode) {
    return
  }
  if (pageConfigCache.value[tab.pageCode]) {
    return
  }
  if (props.previewConfigs[tab.pageCode]) {
    pageConfigCache.value = { ...pageConfigCache.value, [tab.pageCode]: props.previewConfigs[tab.pageCode] }
    emit('page-loaded', tab, props.previewConfigs[tab.pageCode])
    return
  }
  loadingMap.value[tab.id] = true
  errorMap.value[tab.id] = ''
  try {
    let cfg: unknown = null
    if (tab.pageType === TabPageType.FORM) {
      let res: LowCodeFormConfig | null = await getFormByCode(tab.pageCode)
      if (!res && props.allowDraft) {
        const page = await listForms({ page: 1, size: 1, code: tab.pageCode })
        res = page.records.find((item) => item.code === tab.pageCode) ?? null
      }
      const raw = res?.formConfig
      if (raw && typeof raw === 'string' && raw.trim()) cfg = JSON.parse(raw) as FormConfig
    } else if (tab.pageType === TabPageType.LIST) {
      let res: LowCodeListConfig | null = await getListByCode(tab.pageCode)
      if (!res && props.allowDraft) {
        const page = await listLists({ page: 1, size: 1, code: tab.pageCode })
        res = page.records.find((item) => item.code === tab.pageCode) ?? null
      }
      const raw = res?.listConfig
      if (raw && typeof raw === 'string' && raw.trim()) cfg = JSON.parse(raw) as ListConfig
    } else if (tab.pageType === TabPageType.RELATED_PAGE) {
      let res: LowCodeRelatedPageConfig | null = await getRelatedPageByCode(tab.pageCode)
      if (!res && props.allowDraft) {
        const page = await listRelatedPages({ page: 1, size: 1, code: tab.pageCode })
        res = page.records.find((item) => item.code === tab.pageCode) ?? null
      }
      const raw = res?.relatedConfig
      if (raw && typeof raw === 'string' && raw.trim()) cfg = JSON.parse(raw) as RelatedPageConfig
    }
    if (cfg) {
      pageConfigCache.value = { ...pageConfigCache.value, [tab.pageCode]: cfg }
      emit('page-loaded', tab, cfg)
    } else {
      errorMap.value[tab.id] = '引用的页面配置为空或不存在'
    }
  } catch (e) {
    errorMap.value[tab.id] = (e as Error).message || '加载失败'
    console.warn(`[LowCodeTabRenderer] 加载子页面配置失败: ${tab.pageCode}`, e)
  } finally {
    loadingMap.value[tab.id] = false
  }
}

watch(
  activeName,
  (name) => {
    const tab = visibleTabs.value.find((t) => t.name === name)
    if (tab && tab.lazy !== false) {
      loadPageConfig(tab)
    }
    if (tab) {
      emit('tab-change', tab)
      emit('update:modelValue', name)
    }
  },
  { immediate: true }
)

watch(
  visibleTabs,
  (tabs) => {
    for (const tab of tabs) {
      if (tab.lazy === false) {
        loadPageConfig(tab)
      }
    }
  },
  { immediate: true }
)

function handleTabClick(tabName: string) {
  activeName.value = tabName
}

function handleCustomNavigate(tab: TabItemConfig) {
  if (!tab.pageUrl) {
    ElMessage.warning('自定义页面未配置 pageUrl')
    return
  }
  const resolved = resolveTemplate(tab.pageUrl, {
    row: (props.contextData.row as Record<string, unknown>) || {},
    context: (props.contextData.context as Record<string, unknown>) || {},
    route,
    user: (userStore.userInfo as Record<string, unknown>) || {}
  })
  emit('navigate', resolved, tab)
  if (resolved.startsWith('http')) {
    window.open(resolved, '_blank')
  } else {
    router.push(resolved).catch(() => {
      window.open(resolved, '_blank')
    })
  }
}
</script>

<template>
  <div class="low-code-tab-renderer">
    <el-tabs
      v-model="activeName"
      :type="tabsType"
      :tab-position="config.tabPosition || 'top'"
      :closable="config.closable"
      :addable="config.addable"
      :editable="config.editable"
      @tab-click="(pane: TabsPaneContext) => handleTabClick(String(pane.props.name ?? ''))"
    >
      <el-tab-pane
        v-for="tab in visibleTabs"
        :key="tab.id"
        :label="tab.title"
        :name="tab.name"
        :lazy="tab.lazy !== false"
        :disabled="tab.disabled"
      >
        <template #label>
          <span class="tab-label">
            <el-icon v-if="tab.icon"><component :is="tab.icon" /></el-icon>
            <span>{{ tab.title }}</span>
          </span>
        </template>

        <div v-if="tab.pageType === 'form'" class="tab-content" v-loading="loadingMap[tab.id]">
          <div v-if="errorMap[tab.id]" class="tab-error">
            <el-alert :title="`加载表单失败：${errorMap[tab.id]}`" type="error" :closable="false" />
          </div>
          <LowCodeFormRenderer
            v-else-if="pageConfigCache[tab.pageCode ?? '']"
            :config="pageConfigCache[tab.pageCode ?? ''] as FormConfig"
            :model-value="resolveProps(tab)"
          />
        </div>

        <div v-else-if="tab.pageType === 'list'" class="tab-content" v-loading="loadingMap[tab.id]">
          <div v-if="errorMap[tab.id]" class="tab-error">
            <el-alert :title="`加载列表失败：${errorMap[tab.id]}`" type="error" :closable="false" />
          </div>
          <LowCodeListRenderer
            v-else-if="pageConfigCache[tab.pageCode ?? '']"
            :config="pageConfigCache[tab.pageCode ?? ''] as ListConfig"
            :auto-fetch="true"
          />
        </div>

        <div v-else-if="tab.pageType === 'related-page'" class="tab-content" v-loading="loadingMap[tab.id]">
          <div v-if="errorMap[tab.id]" class="tab-error">
            <el-alert :title="`加载关联页失败：${errorMap[tab.id]}`" type="error" :closable="false" />
          </div>
          <LowCodeRelatedPageRenderer
            v-else-if="pageConfigCache[tab.pageCode ?? '']"
            :config="pageConfigCache[tab.pageCode ?? ''] as RelatedPageConfig"
            :allow-draft="allowDraft"
            :context-data="resolveProps(tab)"
          />
        </div>

        <div v-else-if="tab.pageType === 'custom'" class="tab-content">
          <div v-if="tab.pageUrl" class="custom-page">
            <iframe
              v-if="tab.pageUrl.startsWith('http')"
              :src="resolveTemplate(tab.pageUrl, {
                row: (contextData.row as Record<string, unknown>) || {},
                context: (contextData.context as Record<string, unknown>) || {},
                route,
                user: (userStore.userInfo as Record<string, unknown>) || {}
              })"
              frameborder="0"
              class="custom-iframe"></iframe>
            <div v-else class="custom-router">
              <el-button type="primary" @click="handleCustomNavigate(tab)">打开页面</el-button>
              <span class="custom-router-tip">{{ tab.pageUrl }}</span>
            </div>
          </div>
          <el-empty v-else description="自定义页面未配置 URL" :image-size="80" />
        </div>

        <el-empty
          v-else
          :description="`未知的页面类型: ${tab.pageType}`"
          :image-size="80"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.low-code-tab-renderer {
  width: 100%;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.tab-content {
  min-height: 200px;
  padding: 8px 0;
}

.tab-error {
  padding: 8px 0;
}

.custom-page {
  width: 100%;
}

.custom-iframe {
  width: 100%;
  min-height: 600px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.custom-router {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.custom-router-tip {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  font-family: monospace;
}
</style>
