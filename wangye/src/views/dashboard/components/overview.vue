<template>
  <section class="overview cm-surface">
    <header class="overview__header">
      <div>
        <h2>今日经营概览</h2>
        <p>{{ days[1] }} · 数据来自当前门店</p>
      </div>
      <router-link to="/statistics">查看经营统计</router-link>
    </header>
    <div class="overview__metrics">
      <article v-for="metric in metrics" :key="metric.label" class="overview__metric">
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
        <small>{{ metric.hint }}</small>
      </article>
    </div>
  </section>
</template>

<script lang="ts">
import { Component, Vue, Prop } from 'vue-property-decorator'
import { getday } from '@/utils/formValidate'

@Component({ name: 'Overview' })
export default class extends Vue {
  @Prop({ default: () => ({}) }) private readonly overviewData!: any

  get days() {
    return getday()
  }

  get metrics() {
    const data = this.overviewData || {}
    return [
      { label: '实收金额', value: `¥ ${this.money(data.turnover)}`, hint: '今日营业额' },
      { label: '有效订单', value: this.number(data.validOrderCount), hint: '已支付订单数' },
      { label: '订单完成率', value: `${this.rate(data.orderCompletionRate)}%`, hint: '完成订单占比' },
      { label: '平均客单价', value: `¥ ${this.money(data.unitPrice)}`, hint: '每笔有效订单' },
    ]
  }

  private money(value: any) {
    return Number(value || 0).toFixed(2)
  }

  private number(value: any) {
    return Number(value || 0).toLocaleString('zh-CN')
  }

  private rate(value: any) {
    return (Number(value || 0) * 100).toFixed(0)
  }
}
</script>

<style lang="scss" scoped>
.overview { padding: $cm-space-5; }
.overview__header { display: flex; justify-content: space-between; align-items: flex-start; }
h2 { margin: 0 0 4px; color: $cm-text-primary; font-size: 16px; font-weight: 600; }
p { margin: 0; color: $cm-text-secondary; font-size: 12px; }
a { color: $cm-primary; font-size: 13px; }
.overview__metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin-top: $cm-space-5; }
.overview__metric { padding: 2px $cm-space-5; border-right: 1px solid $cm-border; }
.overview__metric:first-child { padding-left: 0; }
.overview__metric:last-child { border-right: 0; }
.overview__metric span, .overview__metric small { display: block; color: $cm-text-secondary; font-size: 12px; }
.overview__metric strong { display: block; margin: 10px 0 6px; color: $cm-text-primary; font-size: 26px; font-weight: 600; line-height: 32px; }
</style>
