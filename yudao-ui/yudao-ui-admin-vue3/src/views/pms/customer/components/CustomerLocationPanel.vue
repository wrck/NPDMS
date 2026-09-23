<template>
  <section class="customer-section">
    <div class="customer-section-heading">
      <h3>地点与站点</h3>
      <span>地点引用、名下站点及站点内位置</span>
    </div>
    <el-table
      v-loading="loading"
      :data="rows"
      row-key="key"
      default-expand-all
      :tree-props="{ children: 'children' }"
      empty-text="暂无地点引用或关联站点"
    >
      <el-table-column prop="name" label="名称 / 地址" min-width="280" show-overflow-tooltip />
      <el-table-column label="类型" width="120">
        <template #default="{ row }">{{ row.typeLabel }}</template>
      </el-table-column>
      <el-table-column label="编码" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.code || '—' }}</template>
      </el-table-column>
      <el-table-column label="生效时间" width="120">
        <template #default="{ row }">{{
          row.effectiveFrom ? formatNullableDate(row.effectiveFrom) : '—'
        }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag
            v-if="row.status !== undefined"
            :type="row.status === 0 ? 'success' : 'info'"
            size="small"
            >{{ row.status === 0 ? '正常' : '停用' }}</el-tag
          >
          <span v-else>—</span>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="siteLoadFailed" class="mt-8px text-12px text-[var(--el-text-color-secondary)]"
      >关联站点信息需要资产地点查询权限，当前仅展示地点引用。</div
    >
  </section>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { handleTree } from '@/utils/tree'
import * as LocationApi from '@/api/pms/asset/location'
import type { AddressVO, SiteLocationVO, SiteVO } from '@/api/pms/asset/location'
import type { CustomerDetailRespVO, CustomerLocationVO } from '@/api/pms/customer'
import { formatNullableDate } from '@/utils/formatTime'

interface LocationRow {
  key: string
  name: string
  typeLabel: string
  code?: string
  effectiveFrom?: string
  status?: number
  children?: LocationRow[]
}

const props = defineProps<{ customer: CustomerDetailRespVO }>()
const rows = ref<LocationRow[]>([])
const loading = ref(false)
const siteLoadFailed = ref(false)

const addressCache = new Map<number, Promise<AddressVO | undefined>>()
const loadAddress = (id: number) => {
  let cached = addressCache.get(id)
  if (!cached) {
    cached = LocationApi.getAddress(id).catch(() => undefined)
    addressCache.set(id, cached)
  }
  return cached
}

const toLocationRow = (node: SiteLocationVO): LocationRow => ({
  key: `location-${node.id}`,
  name: node.name ?? '',
  typeLabel: node.locationType || '位置',
  code: node.code,
  status: node.status,
  children: node.children?.length ? node.children.map(toLocationRow) : undefined
})

const buildSiteRow = async (
  siteId: number,
  key: string,
  effectiveFrom?: string,
  known?: SiteVO
): Promise<LocationRow> => {
  const site = known ?? (await LocationApi.getSite(siteId).catch(() => undefined))
  let name = site?.name || `站点 #${siteId}`
  if (site?.addressId) {
    const address = await loadAddress(site.addressId)
    if (address?.fullAddress) name = `${name}（${address.fullAddress}）`
  }
  const row: LocationRow = {
    key,
    name,
    typeLabel: '站点',
    code: site?.code,
    effectiveFrom,
    status: site?.status
  }
  if (site) {
    try {
      const tree = handleTree(await LocationApi.getSiteLocationTree(siteId), 'id', 'parentId')
      if (tree.length) row.children = tree.map(toLocationRow)
    } catch {
      // 位置树不可读时保留站点行本身
    }
  }
  return row
}

const build = async () => {
  loading.value = true
  siteLoadFailed.value = false
  try {
    const addressRows: Promise<LocationRow>[] = []
    const siteRows: Promise<LocationRow>[] = []
    const referencedSiteIds = new Set<number>()
    for (const reference of props.customer.locations || []) {
      const item = reference as CustomerLocationVO
      if (item.locationType === 'ADDRESS') {
        addressRows.push(
          loadAddress(item.locationId).then((address) => ({
            key: `address-${item.locationId}`,
            name: address?.fullAddress || `地址 #${item.locationId}（暂不可读）`,
            typeLabel: '地点',
            effectiveFrom: item.effectiveFrom,
            status: address?.status
          }))
        )
      } else if (item.locationType === 'SITE') {
        referencedSiteIds.add(item.locationId)
        siteRows.push(
          buildSiteRow(item.locationId, `site-ref-${item.locationId}`, item.effectiveFrom)
        )
      }
    }
    try {
      const page = await LocationApi.getSitePage({
        pageNo: 1,
        pageSize: 100,
        customerId: props.customer.id
      })
      for (const site of page.list || []) {
        if (!site.id || referencedSiteIds.has(site.id)) continue
        siteRows.push(buildSiteRow(site.id, `site-${site.id}`, undefined, site))
      }
    } catch {
      siteLoadFailed.value = true
    }
    rows.value = await Promise.all([...addressRows, ...siteRows])
  } finally {
    loading.value = false
  }
}

watch(
  () => props.customer.id,
  () => {
    void build()
  },
  { immediate: true }
)
</script>
