<template>
  <main class="cm-page dashboard-container statistics-page">
    <PageHeader title="数据统计" description="按时间范围查看门店经营、用户、订单与商品销量" />
    <TitleIndex :flag="flag" :tate-data="tateData" @sendTitleInd="getTitleNum" />

    <section v-if="loading" class="statistics-loading cm-surface" data-testid="statistics-loading">
      正在加载经营数据…
    </section>
    <EmptyState
      v-else-if="hasError"
      class="cm-surface"
      type="error"
      title="统计数据加载失败"
      description="请稍后重试，或检查服务连接"
    >
      <template #action><el-button type="primary" size="small" @click="getTitleNum(flag)">重新加载</el-button></template>
    </EmptyState>
    <EmptyState
      v-else-if="!hasChartData"
      class="cm-surface"
      data-testid="statistics-empty"
      title="所选时间范围暂无经营数据"
      description="切换其他时间范围后再查看"
    />

    <template v-else>
    <div class="statistics-grid">
      <!-- 营业额统计 -->
      <TurnoverStatistics :turnoverdata="turnoverData" />
      <!-- end -->
      <!-- 用户统计 -->
      <UserStatistics :userdata="userData" />
      <!-- end -->
    </div>
    <div class="statistics-grid">
      <!-- 订单统计 -->
      <OrderStatistics :orderdata="orderData" :overviewData="overviewData" />
      <!-- end -->
      <!-- 销量排名TOP10 -->
      <Top :top10data="top10Data" />
      <!-- end -->
    </div>
    </template>
  </main>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import {
  get1stAndToday,
  past7Day,
  past30Day,
  pastWeek,
  pastMonth,
} from '@/utils/formValidate'
import {
  getTurnoverStatistics,
  getUserStatistics,
  getOrderStatistics,
  getTop,
} from '@/api/index'
import PageHeader from '@/components/PageHeader/index.vue'
import EmptyState from '@/components/EmptyState/index.vue'
// 组件
// 标题
import TitleIndex from './components/titleIndex.vue'
// 营业额统计
import TurnoverStatistics from './components/turnoverStatistics.vue'
// 用户统计
import UserStatistics from './components/userStatistics.vue'
// 订单统计
import OrderStatistics from './components/orderStatistics.vue'
// 排名
import Top from './components/top10.vue'
@Component({
  name: 'Dashboard',
  components: {
    TitleIndex,
    TurnoverStatistics,
    UserStatistics,
    OrderStatistics,
    Top,
    PageHeader,
    EmptyState,
  },
})
export default class extends Vue {
  private loading = true
  private hasError = false
  private overviewData = {} as any
  private flag = 2
  private tateData = []
  private turnoverData = {} as any
  private userData = {}
  private orderData = {
    data: {},
  } as any
  private top10Data = {}
  created() {
    this.getTitleNum(2)
  }

  get hasChartData() {
    return Boolean(
      (this.turnoverData.dateList && this.turnoverData.dateList.length) ||
      ((this.orderData.data || {}).dateList || []).length
    )
  }

  // 获取基本数据
  async init(begin: any, end: any) {
    this.loading = true
    this.hasError = false
    try {
      await Promise.all([
        this.getTurnoverStatisticsData(begin, end),
        this.getUserStatisticsData(begin, end),
        this.getOrderStatisticsData(begin, end),
        this.getTopData(begin, end),
      ])
    } catch (error) {
      this.hasError = true
    } finally {
      this.loading = false
    }
  }

  private splitList(value: any) {
    return value ? String(value).split(',') : []
  }

  // 获取营业额统计数据
  async getTurnoverStatisticsData(begin: any ,end:any) {
    const data = await getTurnoverStatistics({ begin: begin,end:end })
    const turnoverData = data.data.data
    this.turnoverData = {
      dateList: this.splitList(turnoverData.dateList),
      turnoverList: this.splitList(turnoverData.turnoverList)
    }
    // this.tateData = this.turnoverData.date
    // const arr = []
    // this.tateData.forEach((val) => {
    //   let date = new Date()
    //   let year = date.getFullYear()
    //   arr.push(year + '-' + val)
    // })
    // this.tateData = arr
  }
  // 获取用户统计数据
  async getUserStatisticsData(begin: any ,end:any) {
    const data = await getUserStatistics({ begin: begin,end:end })
    const userData = data.data.data
    this.userData = {
      dateList: this.splitList(userData.dateList),
      totalUserList: this.splitList(userData.totalUserList),
      newUserList: this.splitList(userData.newUserList),
    }
  }
  // 获取订单统计数据
  async getOrderStatisticsData(begin: any ,end:any) {
    const data = await getOrderStatistics({begin: begin,end:end })
    const orderData = data.data.data
    this.orderData = {
      data: {
        dateList: this.splitList(orderData.dateList),
        orderCountList: this.splitList(orderData.orderCountList),
        validOrderCountList: this.splitList(orderData.validOrderCountList),
        //orderCompletionRateList: orderData.orderCompletionRateList.split(','),
      },
      totalOrderCount: orderData.totalOrderCount,
      validOrderCount: orderData.validOrderCount,
      orderCompletionRate: orderData.orderCompletionRate
    }
  }
  // 获取排行数据
  async getTopData(begin: any ,end:any) {
    const data = await getTop({begin: begin,end:end })
    const top10Data = data.data.data
    this.top10Data = {
      nameList: this.splitList(top10Data.nameList).reverse(),
      numberList: this.splitList(top10Data.numberList).reverse(),
    }
  }
  // 获取当前选中的tab时间
  getTitleNum(data) {
    this.flag = data
    switch (data) {
      case 1:
        this.tateData = get1stAndToday()
        break
      case 2:
        this.tateData = past7Day()
        break
      case 3:
        this.tateData = past30Day()
        break
      case 4:
        this.tateData = pastWeek()
        break
      case 5:
        this.tateData = pastMonth()
        break
    }
    this.init(this.tateData[0], this.tateData[1])
  }
}
</script>

<style lang="scss" scoped>
.statistics-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: $cm-space-4;
  margin-bottom: $cm-space-4;
}

.statistics-loading {
  padding: 72px 20px;
  color: $cm-text-secondary;
  text-align: center;
}

@media (max-width: 1180px) {
  .statistics-grid { grid-template-columns: 1fr; }
}
</style>

<style lang="scss">
.statistics-page .statistics-grid > .container {
  width: auto;
  min-width: 0;
  padding: 20px;
  margin: 0;
  background: #fff;
  border: 1px solid #dfe5eb;
  border-radius: 8px;
}

.statistics-page .homeTitle {
  margin-bottom: 18px;
  color: #1f3449;
  font-size: 16px;
  font-weight: 600;
}
</style>
