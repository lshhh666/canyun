<template>
  <view class="orders-page">
    <cloudmeal-header title="订单中心" subtitle="查看进行中与历史订单" />

    <view class="segment-tabs" role="tablist">
      <button
        v-for="segment in segments"
        :key="segment.key"
        class="segment-tab"
        :class="{ 'segment-tab--active': activeSegment === segment.key }"
        @click="changeSegment(segment.key)"
      >
        {{ segment.label }}
      </button>
    </view>

    <scroll-view class="orders-scroll" scroll-y @scrolltolower="loadNextPage">
      <view v-if="visibleOrders.length" class="orders-list">
        <view v-for="item in visibleOrders" :key="item.id" class="order-card">
          <view class="order-card__header">
            <text class="order-card__time">{{ item.orderTime }}</text>
            <text class="order-card__status">{{ statusWord(item.status) }}</text>
          </view>

          <view class="order-card__body" @click="goDetail(item.id)">
            <view class="dish-summary">
              <image
                v-if="item.orderDetailList && item.orderDetailList.length"
                class="dish-summary__image"
                mode="aspectFill"
                :src="item.orderDetailList[0].image"
              />
              <view class="dish-summary__copy">
                <text class="dish-summary__name">{{ dishSummary(item.orderDetailList) }}</text>
                <text class="dish-summary__count">共 {{ dishCount(item.orderDetailList) }} 件</text>
              </view>
            </view>
            <view class="order-total">
              <text class="order-total__label">实付</text>
              <text class="order-total__amount">￥{{ money(item.amount) }}</text>
              <text class="order-total__arrow">›</text>
            </view>
          </view>

          <view v-if="getOrderActions(item.status, item.orderTime).length" class="order-actions">
            <button
              v-if="hasAction(item, 'pay')"
              class="order-action order-action--primary"
              @click="goPay(item)"
            >继续支付</button>
            <button
              v-if="hasAction(item, 'reminder')"
              class="order-action order-action--primary"
              @click="handleReminder('center', item.id)"
            >催单</button>
            <button
              v-if="hasAction(item, 'repeat')"
              class="order-action order-action--primary"
              @click="oneMoreOrder(item)"
            >再来一单</button>
          </view>
        </view>

        <view class="list-tip">{{ canLoadMore ? '上拉加载更多' : '没有更多订单了' }}</view>
      </view>

      <empty
        v-else-if="hasLoaded && !isLoading && !canLoadMore"
        :text-label="activeSegment === 'current' ? '暂无当前订单' : '暂无历史订单'"
      />
      <view v-else class="list-tip">{{ loadError || '订单加载中…' }}</view>
    </scroll-view>

    <uni-popup ref="commonPopup" class="comPopupBox">
      <view class="popup-content">
        <view class="text">{{ textTip }}</view>
        <view class="btn"><view @click="closePopup">知道了</view></view>
      </view>
    </uni-popup>

    <app-tabbar active="orders" />
  </view>
</template>

<script>
import { getOrderPage, repetitionOrder, reminderOrder, delShoppingCart } from '../api/api.js'
import { mapMutations } from 'vuex'
import Empty from '@/components/empty/empty.vue'
import CloudmealHeader from '@/components/cloudmeal-header/cloudmeal-header.vue'
import AppTabbar from '@/components/app-tabbar/app-tabbar.vue'
import { statusWord, getOvertime } from '@/utils/index.js'
import { filterOrdersBySegment, getOrderActions as resolveOrderActions } from '@/utils/order-segments.js'

export default {
  components: { Empty, CloudmealHeader, AppTabbar },
  data() {
    return {
      segments: [
        { key: 'current', label: '当前订单' },
        { key: 'history', label: '历史订单' },
      ],
      activeSegment: 'current',
      recentOrdersList: [],
      pageInfo: { page: 1, pageSize: 10, total: 0 },
      loadedPages: {},
      failedPage: null,
      isLoading: false,
      hasLoaded: false,
      loadError: '',
      textTip: '',
    }
  },
  computed: {
    visibleOrders() {
      return filterOrdersBySegment(this.recentOrdersList, this.activeSegment)
    },
    totalPages() {
      return Math.ceil(Number(this.pageInfo.total) / Number(this.pageInfo.pageSize))
    },
    canLoadMore() {
      if (this.failedPage !== null) return true
      if (!this.hasLoaded) return true
      return this.pageInfo.page < this.totalPages
    },
  },
  onLoad() {
    this.getList()
  },
  onPullDownRefresh() {
    this.resetList()
    return this.getList().finally(() => uni.stopPullDownRefresh())
  },
  methods: {
    ...mapMutations(['setAddressBackUrl', 'setOrderData']),
    statusWord(status) {
      return statusWord(Number(status))
    },
    getOrderActions(status, orderTime) {
      const timeout = Number(status) === 1 && orderTime ? getOvertime(orderTime) <= 0 : false
      return resolveOrderActions(status, { timeout })
    },
    hasAction(item, action) {
      return this.getOrderActions(item.status, item.orderTime).includes(action)
    },
    dishCount(list) {
      return (Array.isArray(list) ? list : []).reduce((total, dish) => total + Number(dish.number || 0), 0)
    },
    dishSummary(list) {
      const names = (Array.isArray(list) ? list : []).map(dish => dish.name).filter(Boolean)
      return names.length ? names.join('、') : '订单菜品'
    },
    money(amount) {
      const value = Number(amount)
      return Number.isFinite(value) ? value.toFixed(2) : '0.00'
    },
    resetList() {
      this.recentOrdersList = []
      this.pageInfo = { ...this.pageInfo, page: 1, total: 0 }
      this.loadedPages = {}
      this.failedPage = null
      this.hasLoaded = false
      this.loadError = ''
    },
    async getList() {
      if (this.isLoading) return false
      this.isLoading = true
      this.loadError = ''
      uni.showLoading({ title: '加载中', mask: true })

      try {
        while (!this.loadedPages[this.pageInfo.page]) {
          const requestPage = this.pageInfo.page
          this.loadedPages = { ...this.loadedPages, [requestPage]: true }
          const res = await getOrderPage({
            page: requestPage,
            pageSize: this.pageInfo.pageSize,
          })
          if (!res || res.code !== 1) {
            throw new Error((res && res.msg) || '订单加载失败，请重试')
          }

          const data = res.data || {}
          const records = Array.isArray(data.records) ? data.records : []
          this.failedPage = null
          this.recentOrdersList = this.recentOrdersList.concat(records)
          this.pageInfo.total = Number(data.total || 0)
          this.hasLoaded = true

          const reachedLastPage = requestPage >= this.totalPages || records.length === 0
          if (this.visibleOrders.length || reachedLastPage) break
          this.pageInfo.page = requestPage + 1
        }
        return true
      } catch (error) {
        this.failedPage = this.pageInfo.page
        delete this.loadedPages[this.pageInfo.page]
        this.loadError = (error && error.message) || '订单加载失败，请重试'
        uni.showToast({ title: this.loadError, icon: 'none' })
        return false
      } finally {
        this.isLoading = false
        uni.hideLoading()
      }
    },
    async loadNextPage() {
      if (this.isLoading || !this.canLoadMore) return false
      if (this.failedPage !== null) {
        this.pageInfo.page = this.failedPage
      } else {
        this.pageInfo.page += 1
      }
      return this.getList()
    },
    async changeSegment(segment) {
      if (segment === this.activeSegment) return false
      this.activeSegment = segment
      if (!this.visibleOrders.length && this.canLoadMore) return this.loadNextPage()
      return true
    },
    goDetail(id) {
      this.setAddressBackUrl('/pages/historyOrder/historyOrder')
      uni.navigateTo({ url: `/pages/details/index?orderId=${id}` })
    },
    goPay(item) {
      if (!this.hasAction(item, 'pay')) return
      this.setOrderData({
        orderNumber: item.number,
        orderAmount: item.amount,
        orderTime: item.orderTime,
      })
      uni.navigateTo({ url: `/pages/pay/index?orderId=${item.id}` })
    },
    async oneMoreOrder(item) {
      if (!this.hasAction(item, 'repeat')) return false
      try {
        await delShoppingCart()
        const res = await repetitionOrder(item.id)
        if (!res || res.code !== 1) throw new Error((res && res.msg) || '加购失败，请重试')
        uni.reLaunch({ url: '/pages/index/index' })
        return true
      } catch (error) {
        uni.showToast({ title: (error && error.message) || '加购失败，请重试', icon: 'none' })
        return false
      }
    },
    async handleReminder(type, id) {
      const item = this.recentOrdersList.find(order => order.id === id)
      if (!item || !this.hasAction(item, 'reminder')) return false
      try {
        const res = await reminderOrder(id)
        if (!res || res.code !== 1) throw new Error((res && res.msg) || '催单失败，请重试')
        this.textTip = '您的催单信息已发出！'
        this.$refs.commonPopup.open(type)
        return true
      } catch (error) {
        uni.showToast({ title: (error && error.message) || '催单失败，请重试', icon: 'none' })
        return false
      }
    },
    closePopup(type) {
      this.$refs.commonPopup.close(type)
    },
  },
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.orders-page {
  min-height: 100vh;
  color: $cm-text;
  background: $cm-page;
}

.segment-tabs {
  display: flex;
  margin: 20rpx 24rpx 0;
  padding: 6rpx;
  background: $cm-surface;
  border: 1rpx solid $cm-border;
  border-radius: $cm-radius-md;
}

.segment-tab {
  height: 70rpx;
  flex: 1;
  margin: 0;
  color: $cm-text-secondary;
  background: transparent;
  border: 0;
  border-radius: 10rpx;
  font-size: 27rpx;
  line-height: 70rpx;
}

.segment-tab::after,
.order-action::after { border: 0; }

.segment-tab--active {
  color: $cm-primary;
  background: $cm-primary-soft;
  font-weight: 600;
}

.orders-scroll {
  height: calc(100vh - 96rpx - env(safe-area-inset-top) - 112rpx - 112rpx - env(safe-area-inset-bottom));
}

.orders-list {
  padding: 20rpx 24rpx calc(32rpx + env(safe-area-inset-bottom));
}

.order-card {
  margin-bottom: 20rpx;
  padding: 26rpx;
  background: $cm-surface;
  border: 1rpx solid $cm-border;
  border-radius: $cm-radius-md;
}

.order-card__header,
.order-card__body,
.dish-summary,
.order-total,
.order-actions {
  display: flex;
  align-items: center;
}

.order-card__header {
  justify-content: space-between;
  padding-bottom: 20rpx;
  border-bottom: 1rpx solid $cm-border;
}

.order-card__time { color: $cm-text-secondary; font-size: 24rpx; }
.order-card__status { color: $cm-primary; font-size: 25rpx; font-weight: 600; }
.order-card__body { justify-content: space-between; padding: 24rpx 0; }
.dish-summary { min-width: 0; flex: 1; }
.dish-summary__image { width: 104rpx; height: 104rpx; flex: none; border-radius: $cm-radius-sm; }
.dish-summary__copy { overflow: hidden; min-width: 0; margin-left: 18rpx; }
.dish-summary__name { display: block; overflow: hidden; color: $cm-text; font-size: 27rpx; text-overflow: ellipsis; white-space: nowrap; }
.dish-summary__count { display: block; margin-top: 10rpx; color: $cm-text-muted; font-size: 23rpx; }
.order-total { flex: none; margin-left: 20rpx; }
.order-total__label { color: $cm-text-secondary; font-size: 22rpx; }
.order-total__amount { margin-left: 8rpx; color: $cm-text; font-size: 29rpx; font-weight: 700; }
.order-total__arrow { margin-left: 10rpx; color: $cm-text-muted; font-size: 38rpx; }

.order-actions {
  justify-content: flex-end;
  padding-top: 20rpx;
  border-top: 1rpx solid $cm-border;
}

.order-action {
  min-width: 176rpx;
  height: 68rpx;
  margin: 0 0 0 16rpx;
  border-radius: $cm-radius-sm;
  font-size: 26rpx;
  line-height: 68rpx;
}

.order-action--primary { color: $cm-surface; background: $cm-primary; }
.list-tip { padding: 34rpx; color: $cm-text-muted; font-size: 24rpx; text-align: center; }

.popup-content {
  overflow: hidden;
  width: 500rpx;
  background: $cm-surface;
  border-radius: $cm-radius-md;
  text-align: center;
}

.popup-content .text { padding: 54rpx 36rpx; color: $cm-text; font-size: 28rpx; }
.popup-content .btn { padding: 24rpx; color: $cm-primary; border-top: 1rpx solid $cm-border; }
</style>
