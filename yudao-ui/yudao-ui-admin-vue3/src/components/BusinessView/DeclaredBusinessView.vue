<template>
  <BusinessEntityHost :owner-module="ownerModule" :entity-type="entityType" :initial-entity-id="entityId"
    :expected-stable-code="stableCode" :readonly="readonly" :allowed-actions="allowedActions" />
</template>
<script setup lang="ts">
import BusinessEntityHost from '@/components/BusinessEntity/BusinessEntityHost.vue'
import type { BusinessViewId } from '@/api/pms/platform/business-view'
defineProps<{ ownerModule: string; entityType: string; entityId: BusinessViewId; stableCode: string; readonly?: boolean; allowedActions: string[] }>()
</script>
