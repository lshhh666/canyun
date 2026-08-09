<template>
  <section class="product-summary cm-surface">
    <header>
      <span>套餐</span>
      <router-link to="/setmeal">管理</router-link>
    </header>
    <strong>{{ total }}</strong>
    <p>当前套餐总数</p>
    <div class="product-summary__status">
      <span><i class="is-on" />启售 {{ number(setMealData.sold) }}</span>
      <span><i />停售 {{ number(setMealData.discontinued) }}</span>
    </div>
    <router-link class="product-summary__add" to="/setmeal/add">+ 新增套餐</router-link>
  </section>
</template>

<script lang="ts">
import { Component, Vue, Prop } from 'vue-property-decorator'

@Component({ name: 'SetMealStatistics' })
export default class extends Vue {
  @Prop({ default: () => ({}) }) private readonly setMealData!: any

  get total() {
    return this.number(Number(this.setMealData.sold || 0) + Number(this.setMealData.discontinued || 0))
  }

  private number(value: any) {
    return Number(value || 0).toLocaleString('zh-CN')
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/brand-tokens';
.product-summary { padding: 18px; }
header { display: flex; justify-content: space-between; color: $cm-text-primary; font-size: 14px; font-weight: 600; }
header a, .product-summary__add { color: $cm-primary; font-size: 12px; font-weight: 400; }
strong { display: block; margin: 20px 0 3px; color: $cm-text-primary; font-size: 28px; font-weight: 600; }
p { margin: 0; color: $cm-text-secondary; font-size: 12px; }
.product-summary__status { display: flex; gap: 12px; margin: 20px 0 16px; color: $cm-text-regular; font-size: 12px; }
.product-summary__status i { display: inline-block; width: 6px; height: 6px; margin-right: 5px; background: #9aa7b4; border-radius: 50%; vertical-align: 1px; }
.product-summary__status i.is-on { background: $cm-success; }
.product-summary__add { display: block; padding-top: 14px; border-top: 1px solid $cm-border; }
</style>
