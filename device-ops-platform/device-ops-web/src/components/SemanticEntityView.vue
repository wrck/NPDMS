<script setup lang="ts">
import SemanticValueTree from '@/components/SemanticValueTree.vue'

defineProps<{ projections: Record<string, unknown> }>()
</script>

<template>
  <div class="entity-view">
    <el-empty v-if="!Object.keys(projections).length" :image-size="40" description="当前版本未生成业务实体" />
    <section v-for="(entity, entityName) in projections" v-else :key="entityName" class="entity-view__group">
      <h5>{{ entityName }}</h5>
      <SemanticValueTree :value="entity" :path="String(entityName)" />
    </section>
  </div>
</template>

<style scoped>
.entity-view { display: grid; gap: 0.75rem; }
.entity-view__group h5 { margin: 0 0 0.5rem; font-size: 0.8125rem; }
</style>
