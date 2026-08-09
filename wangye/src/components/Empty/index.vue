<template>
  <EmptyState
    :title="isSearch ? '未找到符合条件的内容' : '暂无数据'"
    :description="isSearch ? '请调整筛选条件后重试' : '相关内容会在创建后显示在这里'"
  />
</template>

<script lang="ts">
import { Vue, Component, Prop } from 'vue-property-decorator'
import EmptyState from '@/components/EmptyState/index.vue'

@Component({
  name: 'Empty',
  components: { EmptyState },
})
export default class extends Vue {
  @Prop({ default: false }) private readonly isSearch!: boolean
}
</script>
