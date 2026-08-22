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
          :label="`${site.code} ${site.name}`"
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
      <el-row :gutter="12">
        <el-col :span="6"
          ><el-input v-model="addressDraft.countryCode" placeholder="国家编码"
        /></el-col>
        <el-col :span="6"
          ><el-input v-model="addressDraft.countryName" placeholder="国家"
        /></el-col>
        <el-col :span="6"
          ><el-input v-model="addressDraft.provinceCode" placeholder="省编码"
        /></el-col>
        <el-col :span="6"><el-input v-model="addressDraft.provinceName" placeholder="省" /></el-col>
        <el-col :span="6" class="mt-10px"
          ><el-input v-model="addressDraft.cityCode" placeholder="市编码"
        /></el-col>
        <el-col :span="6" class="mt-10px"
          ><el-input v-model="addressDraft.cityName" placeholder="市"
        /></el-col>
        <el-col :span="6" class="mt-10px"
          ><el-input v-model="addressDraft.districtCode" placeholder="区县编码"
        /></el-col>
        <el-col :span="6" class="mt-10px"
          ><el-input v-model="addressDraft.districtName" placeholder="区/县"
        /></el-col>
        <el-col :span="24" class="mt-10px"
          ><el-input v-model="addressDraft.detailAddress" placeholder="详细地址"
        /></el-col>
      </el-row>
      <el-divider content-position="left">站点</el-divider>
      <el-row :gutter="12">
        <el-col :span="10"><el-input v-model="siteDraft.code" placeholder="站点编码" /></el-col>
        <el-col :span="14"><el-input v-model="siteDraft.name" placeholder="站点名称" /></el-col>
      </el-row>
      <el-divider content-position="left">站点内位置（可选）</el-divider>
      <el-row :gutter="12">
        <el-col :span="8"
          ><el-input v-model="siteLocationDraft.code" placeholder="位置编码"
        /></el-col>
        <el-col :span="8"
          ><el-input v-model="siteLocationDraft.name" placeholder="位置名称"
        /></el-col>
        <el-col :span="8"
          ><el-input v-model="siteLocationDraft.locationType" placeholder="楼栋/楼层/机房/机柜"
        /></el-col>
      </el-row>
      <div class="mt-8px text-12px text-gray-500"
        >位置树不限定层级；后续可在站点树中继续向下维护。</div
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
  address: {},
  site: {},
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
  draft.fallbackLocation = site ? `${site.code || ''} ${site.name || ''}`.trim() : undefined
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
        expectedVersion: location.version,
        code: '',
        name: '',
        locationType: '',
        treeSort: 0
      }
    : undefined
  const site = sites.value.find((item) => item.id === selectedSiteId.value)
  draft.fallbackLocation = [site?.name, location?.name].filter(Boolean).join(' / ')
}

watch(
  () => props.projectId,
  (projectId) => (draft.projectId = projectId)
)
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
  },
  { immediate: true, deep: true }
)
watch(draft, () => emit('update:modelValue', JSON.parse(JSON.stringify(draft))), { deep: true })
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
