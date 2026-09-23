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
          :label="siteLabel(site)"
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
        default-expand-all
        clearable
        class="mt-12px !w-full"
        placeholder="可选：选择机房、楼层、机柜等任意层级位置"
        @change="selectLocation"
      />
    </template>

    <template v-else-if="mode === 'new'">
      <el-divider content-position="left">地址</el-divider>
      <template v-if="customerId">
        <div class="text-12px text-gray-500">关联客户：{{ customerName }}</div>
        <el-select
          v-model="selectedCustomerAddressId"
          filterable
          clearable
          class="mt-10px !w-full"
          placeholder="选择客户已有地址，或清空后在下方新建"
          @change="selectCustomerAddress"
        >
          <el-option
            v-for="address in customerAddresses"
            :key="address.id"
            :label="address.fullAddress"
            :value="address.id ?? 0"
          />
        </el-select>
      </template>
      <template v-if="!selectedCustomerAddressId">
        <!-- 国家默认中国不提供录入入口，countryCode/countryName 由 emptyDraft 固定为 CN/中国 -->
        <PmsDivisionInput
          v-model="addressDraft"
          label="省市区"
          :class="customerId ? 'mt-10px' : ''"
        />
        <el-input v-model="addressDraft.detailAddress" placeholder="详细地址" class="mt-10px" />
      </template>
      <el-divider content-position="left">站点</el-divider>
      <el-input v-model="siteDraft.name" placeholder="站点名称" />
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

    <template v-if="extraAvailable">
      <el-divider content-position="left">站点内位置（可选）</el-divider>
      <el-row :gutter="12">
        <el-col :span="12"
          ><el-input v-model="extraDraft.name" placeholder="位置名称"
        /></el-col>
        <el-col :span="12">
          <el-select
            v-model="extraDraft.locationType"
            clearable
            class="!w-full"
            placeholder="请选择位置类型"
          >
            <el-option
              v-for="dict in getStrDictOptions(DICT_TYPE.PMS_SITE_LOCATION_TYPE)"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-col>
      </el-row>
      <div v-if="extraDraft.name || extraDraft.locationType" class="mt-8px">
        <el-button
          type="primary"
          plain
          size="small"
          :disabled="!extraDraft.name.trim()"
          @click="addExtraLocation"
          ><Icon icon="ep:plus" class="mr-5px" />添加下一级</el-button
        >
      </div>
      <div v-if="chainItems.length" class="mt-8px location-extra-list">
        <el-tag
          v-for="(item, index) in chainItems"
          :key="`${item.name}-${index}`"
          closable
          size="small"
          @close="removeChainItem(index)"
          >{{ item.name }}（{{ locationTypeLabel(item.locationType) }}）</el-tag
        >
      </div>
      <div v-if="chainPreviewVisible" class="mt-8px location-path-preview"
        >保存路径：{{ chainPreview }}</div
      >
      <div v-if="mode === 'new'" class="mt-8px text-12px text-gray-500"
        >保存后位置自动追加为站点的下一级；位置树不限定层级，后续可在站点树中继续向下维护。</div
      >
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { handleTree } from '@/utils/tree'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import * as LocationApi from '@/api/pms/asset/location'
import * as ProjectApi from '@/api/pms/project/projects'
import * as CustomerApi from '@/api/pms/customer'
import type {
  AddressVO,
  LocationMaintainRequest,
  SiteLocationVO,
  SiteVO
} from '@/api/pms/asset/location'

const props = defineProps<{
  modelValue?: LocationMaintainRequest
  projectId?: number
}>()
const emit = defineEmits<{ (e: 'update:modelValue', value: LocationMaintainRequest): void }>()

const customerId = ref<number>()
const customerName = ref('')
const customerAddresses = ref<AddressVO[]>([])
const selectedCustomerAddressId = ref<number>()

const emptyDraft = (): LocationMaintainRequest => ({
  projectId: props.projectId,
  address: { countryCode: 'CN', countryName: '中国' },
  site: { siteType: 'CUSTOMER_SITE', customerId: customerId.value },
  siteLocation: { code: '', name: '', locationType: '', treeSort: 0 },
  extraSiteLocations: [],
  fallbackLocation: ''
})
const draft = reactive<LocationMaintainRequest>(emptyDraft())
const addressDraft = computed(() => draft.address!)
const siteDraft = computed(() => draft.site!)
const siteLocationDraft = computed(() => draft.siteLocation!)
const mode = ref<'existing' | 'new' | 'fallback'>('existing')
const sites = ref<SiteVO[]>([])
const addresses = ref<AddressVO[]>([])
const locationTree = ref<SiteLocationVO[]>([])
const selectedSiteId = ref<number>()
const selectedLocationId = ref<number>()
const extraDraft = reactive<{ name: string; locationType: string }>({ name: '', locationType: '' })

const locationTypeLabel = (value?: string) =>
  getStrDictOptions(DICT_TYPE.PMS_SITE_LOCATION_TYPE).find((dict) => dict.value === value)?.label ||
  value ||
  '位置'

// 追加的位置按添加顺序链式落库：新模式首笔即链首，其余依次为上一笔的下级；
// 已有站点模式挂到所选位置（未选时为站点根）之下。地点引用指向链路末端（最深层位置）。
const addExtraLocation = () => {
  const name = extraDraft.name.trim()
  if (!name) return
  if (mode.value === 'new' && !draft.siteLocation?.name) {
    draft.siteLocation = { code: '', name, locationType: extraDraft.locationType, treeSort: 0 }
  } else {
    if (!draft.extraSiteLocations) draft.extraSiteLocations = []
    draft.extraSiteLocations.push({ name, locationType: extraDraft.locationType, treeSort: 0 })
  }
  extraDraft.name = ''
  extraDraft.locationType = ''
}
interface ChainItem {
  name: string
  locationType?: string
  isFirst: boolean
}
const chainItems = computed<ChainItem[]>(() => {
  const items: ChainItem[] = []
  // 已有站点模式的 siteLocation 是引用（只有 id），不进链视图
  if (draft.siteLocation?.name && !draft.siteLocation?.id) {
    items.push({
      name: draft.siteLocation.name,
      locationType: draft.siteLocation.locationType,
      isFirst: true
    })
  }
  for (const extra of draft.extraSiteLocations || []) {
    if (extra.name) items.push({ name: extra.name, locationType: extra.locationType, isFirst: false })
  }
  return items
})
const removeChainItem = (index: number) => {
  if (chainItems.value[index]?.isFirst) {
    const promoted = draft.extraSiteLocations?.shift()
    draft.siteLocation = promoted
      ? { code: '', name: promoted.name, locationType: promoted.locationType, treeSort: 0 }
      : { code: '', name: '', locationType: '', treeSort: 0 }
    return
  }
  const offset = chainItems.value[0]?.isFirst ? 1 : 0
  draft.extraSiteLocations?.splice(index - offset, 1)
}
const extraAvailable = computed(
  () => mode.value === 'new' || (mode.value === 'existing' && !!selectedSiteId.value)
)
const findLocationPath = (
  nodes: SiteLocationVO[],
  id?: number,
  trail: SiteLocationVO[] = []
): SiteLocationVO[] | undefined => {
  for (const node of nodes) {
    const next = [...trail, node]
    if (node.id === id) return next
    const child = findLocationPath(node.children || [], id, next)
    if (child) return child
  }
}
const existingPathPreview = computed(() => {
  const site = sites.value.find((item) => item.id === selectedSiteId.value)
  const path = findLocationPath(locationTree.value, selectedLocationId.value) || []
  return [
    site ? siteLabel(site) : '',
    ...path.map((node) => node.name),
    ...(draft.extraSiteLocations || []).map((extra) => extra.name)
  ]
    .filter(Boolean)
    .join(' / ')
})
const chainPreview = computed(() => {
  if (mode.value === 'existing') return existingPathPreview.value
  return [
    siteDraft.value.name || '（站点名称待填）',
    siteLocationDraft.value.name,
    ...(draft.extraSiteLocations || []).map((extra) => extra.name)
  ]
    .filter(Boolean)
    .join(' / ')
})
const chainPreviewVisible = computed(() =>
  mode.value === 'existing'
    ? !!draft.extraSiteLocations?.length
    : !!(siteDraft.value.name || siteLocationDraft.value.name || draft.extraSiteLocations?.length)
)

const loadSites = async () => {
  const [sitePage, addressPage] = await Promise.all([
    LocationApi.getSitePage({ pageNo: 1, pageSize: 100 }),
    LocationApi.getAddressPage({ pageNo: 1, pageSize: 100 })
  ])
  sites.value = sitePage.list || []
  addresses.value = addressPage.list || []
}
const siteLabel = (site: SiteVO) => {
  const fullAddress = addresses.value.find((item) => item.id === site.addressId)?.fullAddress
  return fullAddress ? `${site.name ?? ''}（${fullAddress}）` : (site.name ?? '')
}

// 站点与客户关联：新站点带 customerId（后端按客户编码生成站点编码前缀并落 ast_site.customer_id），
// 已有站点引用（site.id）不带 customerId，避免更新路径改写既有归属。
const stampSiteCustomer = () => {
  if (mode.value !== 'new' || !draft.site || draft.site.id) return
  draft.site.customerId = customerId.value
}

let customerSequence = 0
const loadCustomerContext = async (projectId?: number) => {
  const current = ++customerSequence
  customerId.value = undefined
  customerName.value = ''
  customerAddresses.value = []
  selectedCustomerAddressId.value = undefined
  if (draft.address?.id) {
    draft.address = { countryCode: 'CN', countryName: '中国' }
    draft.addressText = undefined
  }
  stampSiteCustomer()
  if (!projectId) return
  try {
    const project = await ProjectApi.getProject(projectId)
    if (current !== customerSequence || !project.customerCode) return
    const detail = await CustomerApi.getCustomerByCode(project.customerCode)
    if (current !== customerSequence) return
    customerId.value = detail.id
    customerName.value = detail.name ?? ''
    const references = (detail.locations || []).filter(
      (item: { locationType: string }) => item.locationType === 'ADDRESS'
    )
    const list = await Promise.all(
      references.map((item: { locationId: number }) => LocationApi.getAddress(item.locationId))
    )
    if (current !== customerSequence) return
    customerAddresses.value = list.filter((item) => item.status === 0)
  } catch {
    // 项目客户信息不可读时不阻塞地点维护，仅不提供客户地址选择
  }
  stampSiteCustomer()
}

const selectCustomerAddress = (id?: number) => {
  const address = customerAddresses.value.find((item) => item.id === id)
  if (!address) {
    draft.address = { countryCode: 'CN', countryName: '中国' }
    draft.addressText = undefined
    return
  }
  // 引用客户已有地址：只传 id+expectedVersion（后端 reference-only 语义，不产生版本变更），
  // 完整地址文本放 addressText 供使用方拼接展示。
  draft.address = { id: address.id, expectedVersion: address.version }
  draft.addressText = address.fullAddress
}

const selectSite = async (siteId?: number) => {
  selectedLocationId.value = undefined
  locationTree.value = []
  extraDraft.name = ''
  extraDraft.locationType = ''
  draft.extraSiteLocations = []
  const site = sites.value.find((item) => item.id === siteId)
  draft.address = undefined
  draft.siteLocation = undefined
  draft.fallbackLocation = site ? siteLabel(site) : undefined
  draft.site = site ? { id: site.id, expectedVersion: site.version } : undefined
  if (siteId)
    locationTree.value = handleTree(
      await LocationApi.getSiteLocationTree(siteId),
      'id',
      'parentId'
    )
}

const selectLocation = (id?: number) => {
  const path = findLocationPath(locationTree.value, id)
  const location = path?.[path.length - 1]
  draft.siteLocation = location
    ? {
        id: location.id,
        expectedVersion: location.version
      }
    : undefined
  const site = sites.value.find((item) => item.id === selectedSiteId.value)
  draft.fallbackLocation = [site ? siteLabel(site) : '', location?.name]
    .filter(Boolean)
    .join(' / ')
}

watch(
  () => props.projectId,
  (projectId) => {
    draft.projectId = projectId
    loadCustomerContext(projectId)
  }
)
// radio 的 v-model 同步更新 mode，渲染先于 @change 的 nextTick 回调；
// 必须在渲染前保证 new 模式结构存在，否则 addressDraft 访问 undefined 崩溃。
// existing 模式的 address/site/siteLocation 是引用，缺失即“未选择”，
// 不能被空壳默认值复活，否则保存会误建空地址。
const ensureStructure = () => {
  if (mode.value === 'fallback') return
  if (mode.value === 'new') {
    if (!draft.address) draft.address = { countryCode: 'CN', countryName: '中国' }
    if (!draft.site) draft.site = { siteType: 'CUSTOMER_SITE' }
    if (!draft.siteLocation)
      draft.siteLocation = { code: '', name: '', locationType: '', treeSort: 0 }
  }
  if (!draft.extraSiteLocations) draft.extraSiteLocations = []
}
watch(
  () => props.modelValue,
  async (value) => {
    if (!value) return
    if (JSON.stringify(value) === JSON.stringify(draft)) return
    // 回传值缺失的键语义是“已清空”（引用被清掉或保存侧被置空），
    // 不能用 emptyDraft 默认值复活；结构缺口由 ensureStructure 按当前模式补齐。
    for (const key of [
      'address',
      'site',
      'siteLocation',
      'extraSiteLocations',
      'fallbackLocation',
      'addressText'
    ] as const) {
      if (!(key in value)) draft[key] = undefined
    }
    Object.assign(draft, value)
    if (!value.address && !value.site && value.fallbackLocation) mode.value = 'fallback'
    else if (value.address?.id || value.site?.id) {
      mode.value = 'existing'
      selectedSiteId.value = value.site?.id
      selectedLocationId.value = value.siteLocation?.id
      if (selectedSiteId.value) {
        if (!sites.value.length) await loadSites()
        locationTree.value = handleTree(
          await LocationApi.getSiteLocationTree(selectedSiteId.value),
          'id',
          'parentId'
        )
      }
    } else if (value.address || value.site || value.siteLocation) mode.value = 'new'
    ensureStructure()
  },
  { immediate: true, deep: true }
)
watch(
  draft,
  () => {
    // 已有站点引用的 siteLocation 只带 id，保存文本由 fallbackLocation 承载；
    // 在 emit 前同步读取 computed 派生完整路径（站点/各级位置/追加层级），
    // 不依赖 watcher 触发顺序，保证地点文本体现所有层级。
    if (mode.value === 'existing') draft.fallbackLocation = existingPathPreview.value || undefined
    emit('update:modelValue', JSON.parse(JSON.stringify(draft)))
  },
  { deep: true }
)
watch(mode, ensureStructure)
const changeMode = (value: 'existing' | 'new' | 'fallback') => {
  Object.assign(draft, emptyDraft())
  selectedSiteId.value = undefined
  selectedLocationId.value = undefined
  selectedCustomerAddressId.value = undefined
  extraDraft.name = ''
  extraDraft.locationType = ''
  if (value === 'fallback') {
    draft.address = undefined
    draft.site = undefined
    draft.siteLocation = undefined
  }
}

onMounted(() => {
  loadSites()
  loadCustomerContext(props.projectId)
})
</script>

<style scoped>
.location-selector {
  width: 100%;
  padding: 12px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.location-path-preview {
  color: var(--el-color-primary);
  font-size: 13px;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.location-extra-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
