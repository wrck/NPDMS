<script setup lang="ts">
import '@/styles/management.css'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import CollectionList from '@/components/management/CollectionList.vue'
import CollectionTaskPanel from '@/components/CollectionTaskPanel.vue'
const route = useRoute()
const isDetail = computed(() => Boolean(route.params.collectionId))
// Existing panel restores only query-based deep links, preserving its stream/export safeguards.
</script>
<template>
  <AppShell
    class="management-page"
    element-layout
    title="采集记录"
    subtitle="授权范围历史检索"
  >
    <el-card
      v-if="!isDetail"
      shadow="never"
    >
      <CollectionList />
    </el-card>
    <div
      v-else
      class="detail"
    >
      <RouterLink to="/records">
        返回记录列表
      </RouterLink>
      <CollectionTaskPanel
        :key="route.fullPath"
        readonly
      />
    </div>
  </AppShell>
</template>
