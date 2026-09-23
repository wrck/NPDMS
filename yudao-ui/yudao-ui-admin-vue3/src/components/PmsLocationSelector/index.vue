<template>
  <div class="location-selector">
    <el-radio-group v-model="mode" class="mb-12px" @change="changeMode">
      <el-radio-button value="existing">选择已有地点</el-radio-button>
      <el-radio-button value="new">现场维护新地点</el-radio-button>
      <el-radio-button value="fallback">站点未维护</el-radio-button>
    </el-radio-group>

    <template v-if="mode === 'existing'">
      <el-select
        v-model="selectedSiteId"
        filterable
        clearable
        class="!w-full"
        placeholder="选择站点"
        @change="selectSite"
      >
        <el-option
          v-for="site in sites"
          :key="site.id"
          :label="site.name"
          :value="site.id ?? 0"
        />
      </el-select>
      <el-tree-select
        v-if="selectedSiteId"
        v-model="selectedLocationId"
        :data="locationTree"
        node-key="id"
        :props="{ label: 'name', children: 'children' }"
        check-strictly
        clearable
        class="mt-12px !w-full"
        placeholder="可选：选择机房、楼层、机柜等任意层级位置"
        @change="selectLocation"
      />
    </template>

    <template v-else-if="mode === 'new'">
      <el-divider content-position="left">地址</el-divider>
      <!-- 国家默认中国不提供录入入口，countryCode/countryName 由 emptyDraft 固定为 CN/中国 -->
      <PmsDivisionInput v-model="addressDraft" label="省市区" />
      <el-input v-model="addressDraft.detailAddress" placeholder="详细地址" class="mt-10px" />
      <el-divider content-position="left">站点</el-divider>
      <el-row :gutter="12">
        <el-col :span="10">
          <el-input :model-value="siteDraft.code" disabled placeholder="保存时自动生成" />
        </el-col>
        <el-col :span="14"><el-input v-model="siteDraft.name" placeholder="站点名称" /></el-col>
      </el-row>
      <el-divider content-position="left">站点内位置（可选）</el-divider>
      <el-row :gutter="12">
        <el-col :span="12"
          ><el-input v-model="siteLocationDraft.name" placeholder="位置名称"
        /></el-col>
        <el-col :span="12"
          ><el-input v-model="siteLocationDraft.locationType" placeholder="楼栋/楼层/机房/机柜"
        /></el-col>
      </el-row>
      <div class="mt-8px text-12px text-gray-500"
        >位置编码保存时按站点自动生成；位置树不限定层级，后续可在站点树中继续向下维护。</div
      >
    </template>

    <template v-else>
      <el-alert type="warning" :closable="false" show-icon class="mb-10px">
        将以 UNRESOLVED 保存兼容地点，待工勘或安装时补充结构化地点。
      </el-alert>
      <el-input
        v-model="draft.fallbackLocation"
        type="textarea"
        :rows="2"
        placeholder="请输入现场可识别的地点说明"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import * as LocationApi from '@/api/pms/asset/location'
import type { LocationMaintainRequest, SiteLocationVO, SiteVO } from '@/api/pms/asset/location'

const props = defineProps<{
  modelValue?: LocationMaintainRequest
  projectId?: number
}>()
const emit = defineEmits<{ (e: 'update:modelValue', value: LocationMaintainRequest): void }>()

const emptyDraft = (): LocationMaintainRequest => ({
  projectId: props.projectId,
  address: { countryCode: 'CN', countryName: '中国' },
  site: { siteType: 'CUSTOMER_SITE' },
  siteLocation: { code: '', name: '', locationType: '', treeSort: 0 },
  fallbackLocation: ''
})
const draft = reactive<LocationMaintainRequest>(emptyDraft())
const addressDraft = computed(() => draft.address!)
const siteDraft = computed(() => draft.site!)
const siteLocationDraft = computed(() => draft.siteLocation!)
const mode = ref<'existing' | 'new' | 'fallback'>('existing')
const sites = ref<SiteVO[]>([])
const locationTree = ref<SiteLocationVO[]>([])
const selectedSiteId = ref<number>()
const selectedLocationId = ref<number>()

const loadSites = async () => {
  const page = await LocationApi.getSitePage({ pageNo: 1, pageSize: 100 })
  sites.value = page.list || []
}

const selectSite = async (siteId?: number) => {
  selectedLocationId.value = undefined
  locationTree.value = []
  const site = sites.value.find((item) => item.id === siteId)
  draft.address = undefined
  draft.siteLocation = undefined
  draft.fallbackLocation = site?.name
  draft.site = site ? { id: site.id, expectedVersion: site.version } : undefined
  if (siteId) locationTree.value = await LocationApi.getSiteLocationTree(siteId)
}

const findLocation = (nodes: SiteLocationVO[], id?: number): SiteLocationVO | undefined => {
  for (const node of nodes) {
    if (node.id === id) return node
    const child = findLocation(node.children || [], id)
    if (child) return child
  }
}

const selectLocation = (id?: number) => {
  const location = findLocation(locationTree.value, id)
  draft.siteLocation = location
    ? {
        id: location.id,
        expectedVersion: location.version
      }
    : undefined
  const site = sites.value.find((item) => item.id === selectedSiteId.value)
  draft.fallbackLocation = [site?.name, location?.name].filter(Boolean).join(' / ')
}

watch(
  () => props.projectId,
  (projectId) => (draft.projectId = projectId)
)
// radio 的 v-model 同步更新 mode，渲染先于 @change 的 nextTick 回调；
// 必须在渲染前保证 new 模式结构存在，否则 addressDraft 访问 undefined 崩溃。
// 外部传入的 modelValue 也可能是残缺结构（如保存时 siteLocation 被共享引用置空），
// 渲染前同样补齐，只补缺失不清值。
const ensureStructure = () => {
  if (mode.value === 'fallback') return
  if (!draft.address) draft.address = {}
  if (!draft.site) draft.site = { siteType: 'CUSTOMER_SITE' }
  if (!draft.siteLocation)
    draft.siteLocation = { code: '', name: '', locationType: '', treeSort: 0 }
}
watch(
  () => props.modelValue,
  async (value) => {
    if (!value) return
    if (JSON.stringify(value) === JSON.stringify(draft)) return
    Object.assign(draft, emptyDraft(), value)
    if (!value.address && !value.site && value.fallbackLocation) mode.value = 'fallback'
    else if (value.address?.id || value.site?.id) {
      mode.value = 'existing'
      selectedSiteId.value = value.site?.id
      selectedLocationId.value = value.siteLocation?.id
      if (selectedSiteId.value) {
        if (!sites.value.length) await loadSites()
        locationTree.value = await LocationApi.getSiteLocationTree(selectedSiteId.value)
      }
    } else if (value.address || value.site || value.siteLocation) mode.value = 'new'
    ensureStructure()
  },
  { immediate: true, deep: true }
)
watch(draft, () => emit('update:modelValue', JSON.parse(JSON.stringify(draft))), { deep: true })
watch(mode, ensureStructure)
const changeMode = (value: 'existing' | 'new' | 'fallback') => {
  Object.assign(draft, emptyDraft())
  selectedSiteId.value = undefined
  selectedLocationId.value = undefined
  if (value === 'fallback') {
    draft.address = undefined
    draft.site = undefined
    draft.siteLocation = undefined
  }
}

onMounted(loadSites)
</script>

<style scoped>
.location-selector {
  width: 100%;
  padding: 12px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
</style>
