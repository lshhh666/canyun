<template>
  <main class="cm-page dashboard-container">
    <PageHeader title="工作台" description="集中查看今日经营数据与需要处理的门店事项">
      <template #actions>
        <el-button size="small" :loading="loading" @click="init">刷新数据</el-button>
      </template>
    </PageHeader>

    <section v-if="loading" class="cm-surface dashboard-loading" data-testid="dashboard-loading">
      <div v-for="item in 4" :key="item" class="dashboard-loading__item" />
    </section>

    <EmptyState
      v-else-if="hasError"
      class="cm-surface"
      type="error"
      title="经营数据暂时无法加载"
      description="请检查网络或服务状态后重试"
    >
      <template #action>
        <el-button type="primary" size="small" @click="init">重新加载</el-button>
      </template>
    </EmptyState>

    <template v-else>
      <Overview :overview-data="overviewData" />

      <div class="dashboard-grid">
        <Orderview :orderview-data="orderviewData" />
        <div class="dashboard-products">
          <CuisineStatistics :dishes-data="dishesData" />
          <SetMealStatistics :set-meal-data="setMealData" />
        </div>
      </div>

      <OrderList
        :order-statics="orderStatics"
        @getOrderListBy3Status="getOrderListBy3Status"
      />
    </template>
  </main>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import {
  getBusinessData,
  getOrderData,
  getOverviewDishes,
  getSetMealStatistics,
} from '@/api/index'
import { getOrderListBy } from '@/api/order'
import PageHeader from '@/components/PageHeader/index.vue'
import EmptyState from '@/components/EmptyState/index.vue'
import Overview from './components/overview.vue'
import Orderview from './components/orderview.vue'
import CuisineStatistics from './components/cuisineStatistics.vue'
import SetMealStatistics from './components/setMealStatistics.vue'
import OrderList from './components/orderList.vue'

@Component({
  name: 'Dashboard',
  components: {
    PageHeader,
    EmptyState,
    Overview,
    Orderview,
    CuisineStatistics,
    SetMealStatistics,
    OrderList,
  },
})
export default class extends Vue {
  private loading = true
  private hasError = false
  private overviewData: any = {}
  private orderviewData: any = {}
  private dishesData: any = {}
  private setMealData: any = {}
  private orderStatics: any = {}

  created() {
    this.init()
  }

  async init() {
    this.loading = true
    this.hasError = false
    try {
      const [business, orders, dishes, setMeals] = await Promise.all([
        getBusinessData(),
        getOrderData(),
        getOverviewDishes(),
        getSetMealStatistics(),
      ])
      this.overviewData = business.data.data || {}
      this.orderviewData = orders.data.data || {}
      this.dishesData = dishes.data.data || {}
      this.setMealData = setMeals.data.data || {}
    } catch (error) {
      this.hasError = true
    } finally {
      this.loading = false
    }
  }

  getOrderListBy3Status() {
    getOrderListBy({})
      .then((res) => {
        if (res.data.code === 1) {
          this.orderStatics = res.data.data || {}
        } else {
          this.$message.error(res.data.msg)
        }
      })
      .catch((err) => {
        this.$message.error('请求出错了：' + err.message)
      })
  }
}
</script>

<style lang="scss" scoped>
.dashboard-loading {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: $cm-space-4;
  padding: $cm-space-5;
}

.dashboard-loading__item {
  height: 92px;
  background: linear-gradient(90deg, #f2f5f7 25%, #f8fafb 37%, #f2f5f7 63%);
  background-size: 400% 100%;
  border-radius: $cm-radius-md;
  animation: cm-loading 1.4s ease infinite;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(360px, 1fr);
  gap: $cm-space-4;
  margin: $cm-space-4 0;
}

.dashboard-products {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: $cm-space-4;
}

@keyframes cm-loading {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}

@media (max-width: 1280px) {
  .dashboard-grid { grid-template-columns: 1fr; }
}
</style>

<style lang="scss">
.dashboard-container {
  .homecon.container {
    margin: 0;
    padding: 0;
    overflow: hidden;
    background: #fff;
    border: 1px solid #dfe5eb;
    border-radius: 8px;
  }

  .homeTitleBtn {
    min-height: 56px;
    padding: 16px 20px;
    margin: 0;
    border-bottom: 1px solid #dfe5eb;
  }

  .tableBox { border: 0; }
}
</style>
