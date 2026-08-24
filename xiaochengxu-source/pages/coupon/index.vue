<template>
  <view class="coupon-page">
    <cloudmeal-header
      :title="selectMode ? '选择优惠券' : '优惠券'"
      show-back
      @back="goBack"
    />

    <view v-if="!selectMode" class="coupon-tabs">
      <view
        class="coupon-tab"
        :class="{ 'coupon-tab--active': activeTab === 'center' }"
        @tap="switchTab('center')"
      >领券中心</view>
      <view
        class="coupon-tab"
        :class="{ 'coupon-tab--active': activeTab === 'my' }"
        @tap="switchTab('my')"
      >我的优惠券</view>
    </view>

    <scroll-view class="coupon-scroll" scroll-y>
      <view v-if="selectMode" class="selection-tip">
        <text>优惠券门槛按菜品金额计算</text>
        <text>当前菜品金额 ￥{{ money(goodsAmount) }}</text>
      </view>

      <view
        v-if="selectMode"
        class="coupon-card coupon-card--none"
        @tap="clearSelection"
      >
        <view>
          <text class="coupon-card__name">不使用优惠券</text>
          <text class="coupon-card__desc">按原价提交订单</text>
        </view>
        <view class="coupon-radio" :class="{ 'coupon-radio--checked': !selectedCouponId }"></view>
      </view>

      <state-panel
        v-if="loadState === 'loading'"
        title="优惠券加载中…"
      />
      <state-panel
        v-else-if="loadState === 'error'"
        title="优惠券加载失败"
        description="请检查网络后重试"
        actionText="重新加载"
        @action="loadCoupons"
      />
      <state-panel
        v-else-if="displayCoupons.length === 0"
        :title="emptyTitle"
        :description="emptyDescription"
      />

      <view v-else class="coupon-list">
        <view
          v-for="coupon in displayCoupons"
          :key="coupon.id"
          class="coupon-card"
          :class="{
            'coupon-card--disabled': selectMode && !isSelectable(coupon),
            'coupon-card--selected': selectMode && selectedCouponId === coupon.id
          }"
          @tap="selectMode && selectCoupon(coupon)"
        >
          <view class="coupon-card__amount">
            <view><text class="coupon-card__currency">￥</text>{{ money(coupon.discountAmount) }}</view>
            <text>满{{ money(coupon.thresholdAmount) }}可用</text>
          </view>

          <view class="coupon-card__content">
            <view class="coupon-card__title-row">
              <text class="coupon-card__name">{{ coupon.couponName || coupon.name }}</text>
              <text v-if="activeTab === 'my' || selectMode" class="coupon-status" :class="statusClass(coupon)">
                {{ statusText(coupon) }}
              </text>
            </view>
            <text class="coupon-card__validity">{{ validityText(coupon) }}</text>
            <text v-if="selectMode && !isSelectable(coupon)" class="coupon-card__reason">
              {{ selectionReason(coupon) }}
            </text>
          </view>

          <button
            v-if="!selectMode && activeTab === 'center'"
            class="coupon-action"
            :disabled="isReceived(coupon) || receivingId === coupon.id"
            :loading="receivingId === coupon.id"
            @tap.stop="handleReceive(coupon)"
          >{{ isReceived(coupon) ? '已领取' : '领取' }}</button>

          <view
            v-else-if="selectMode && isSelectable(coupon)"
            class="coupon-radio"
            :class="{ 'coupon-radio--checked': selectedCouponId === coupon.id }"
          ></view>
        </view>
      </view>

      <view class="coupon-bottom-space"></view>
    </scroll-view>
  </view>
</template>

<script>
import CloudmealHeader from '@/components/cloudmeal-header/cloudmeal-header.vue'
import StatePanel from '@/components/state-panel/state-panel.vue'
import { getAvailableCoupons, getMyCoupons, receiveCoupon } from '../api/api.js'
import { getErrorMessage } from '@/utils/error-message.js'
import { couponTimestamp, getCouponEligibility, normalizeCouponStatus } from '@/utils/coupon.js'

export default {
  components: { CloudmealHeader, StatePanel },
  data() {
    return {
      activeTab: 'center',
      selectMode: false,
      goodsAmount: 0,
      availableCoupons: [],
      myCoupons: [],
      loadState: 'idle',
      reloadRequested: false,
      receivingId: null
    }
  },
  computed: {
    displayCoupons() {
      const source = this.activeTab === 'center' ? this.availableCoupons : this.myCoupons
      if (!this.selectMode) return source
      return source.slice().sort((left, right) => {
        const leftSelectable = this.isSelectable(left) ? 1 : 0
        const rightSelectable = this.isSelectable(right) ? 1 : 0
        if (leftSelectable !== rightSelectable) return rightSelectable - leftSelectable
        return Number(right.discountAmount || 0) - Number(left.discountAmount || 0)
      })
    },
    selectedCouponId() {
      const coupon = this.$store.state.selectedCoupon
      return coupon ? coupon.id : null
    },
    emptyTitle() {
      if (this.selectMode) return '暂无可选优惠券'
      return this.activeTab === 'center' ? '暂无可领取优惠券' : '还没有优惠券'
    },
    emptyDescription() {
      if (this.selectMode) return '可以选择不使用优惠券'
      return this.activeTab === 'center' ? '稍后再来看看' : '去领券中心看看吧'
    }
  },
  onLoad(options) {
    const pageOptions = options || {}
    this.selectMode = String(pageOptions.select || '') === '1'
    this.goodsAmount = Math.max(0, Number(pageOptions.goodsAmount) || 0)
    this.activeTab = this.selectMode ? 'my' : (pageOptions.tab === 'my' ? 'my' : 'center')
  },
  onShow() {
    this.loadCoupons()
  },
  methods: {
    goBack() {
      uni.navigateBack({ delta: 1 })
    },
    switchTab(tab) {
      if (this.activeTab === tab) return
      this.activeTab = tab
      return this.loadCoupons()
    },
    async loadCoupons() {
      if (this.loadState === 'loading') {
        this.reloadRequested = true
        return false
      }
      let requestedTab
      do {
        this.reloadRequested = false
        requestedTab = this.activeTab
        this.loadState = 'loading'
        try {
          if (requestedTab === 'center') {
            const results = await Promise.all([getAvailableCoupons(), getMyCoupons()])
            this.availableCoupons = Array.isArray(results[0].data) ? results[0].data : []
            this.myCoupons = Array.isArray(results[1].data) ? results[1].data : []
          } else {
            const result = await getMyCoupons()
            this.myCoupons = Array.isArray(result.data) ? result.data : []
          }
          this.loadState = 'ready'
        } catch (error) {
          if (requestedTab === this.activeTab && !this.reloadRequested) {
            this.loadState = 'error'
            uni.showToast({
              title: getErrorMessage(error, '优惠券加载失败，请重试'),
              icon: 'none'
            })
          }
        }
      } while (this.reloadRequested || requestedTab !== this.activeTab)
      return this.loadState === 'ready'
    },
    async handleReceive(coupon) {
      if (this.receivingId || this.isReceived(coupon)) return
      this.receivingId = coupon.id
      try {
        await receiveCoupon(coupon.id)
        uni.showToast({ title: '领取成功', icon: 'success' })
        await this.loadCoupons()
      } catch (error) {
        uni.showToast({
          title: getErrorMessage(error, '领取失败，请重试'),
          icon: 'none'
        })
      } finally {
        this.receivingId = null
      }
    },
    isReceived(coupon) {
      return this.myCoupons.some(item => Number(item.couponId) === Number(coupon.id))
    },
    normalizedStatus(coupon) {
      return normalizeCouponStatus(coupon)
    },
    statusText(coupon) {
      const text = {
        AVAILABLE: '可使用',
        LOCKED: '已锁定',
        USED: '已使用',
        EXPIRED: '已过期'
      }
      return text[this.normalizedStatus(coupon)] || '未知状态'
    },
    statusClass(coupon) {
      return `coupon-status--${this.normalizedStatus(coupon).toLowerCase()}`
    },
    isSelectable(coupon) {
      return getCouponEligibility(coupon, this.goodsAmount).eligible
    },
    selectionReason(coupon) {
      const status = this.normalizedStatus(coupon)
      if (status !== 'AVAILABLE') return this.statusText(coupon)
      return getCouponEligibility(coupon, this.goodsAmount).reason
    },
    selectCoupon(coupon) {
      if (!this.isSelectable(coupon)) {
        uni.showToast({ title: this.selectionReason(coupon), icon: 'none' })
        return
      }
      this.$store.commit('setSelectedCoupon', coupon)
      this.goBack()
    },
    clearSelection() {
      this.$store.commit('setSelectedCoupon', null)
      this.goBack()
    },
    money(value) {
      const amount = Number(value)
      return Number.isFinite(amount) ? amount.toFixed(2) : '0.00'
    },
    toTimestamp(value) {
      return couponTimestamp(value)
    },
    formatDate(value) {
      if (!value) return ''
      if (Array.isArray(value)) {
        const pad = number => String(number || 0).padStart(2, '0')
        return `${value[0]}.${pad(value[1])}.${pad(value[2])}`
      }
      return String(value).replace('T', ' ').slice(0, 10).replace(/-/g, '.')
    },
    validityText(coupon) {
      const start = this.formatDate(coupon.validStartTime)
      const end = this.formatDate(coupon.validEndTime)
      return start && end ? `有效期 ${start} - ${end}` : '有效期以活动规则为准'
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.coupon-page {
  min-height: 100vh;
  background: $cm-page;
  color: $cm-text;
}

.coupon-tabs {
  display: flex;
  height: 88rpx;
  background: $cm-surface;
  border-bottom: 1rpx solid $cm-border;
}

.coupon-tab {
  position: relative;
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  color: $cm-text-secondary;
  font-size: 28rpx;
}

.coupon-tab--active {
  color: $cm-primary;
  font-weight: 600;
}

.coupon-tab--active::after {
  position: absolute;
  right: 74rpx;
  bottom: 0;
  left: 74rpx;
  height: 5rpx;
  background: $cm-primary;
  border-radius: 5rpx;
  content: '';
}

.coupon-scroll {
  height: calc(100vh - 96rpx - env(safe-area-inset-top));
}

.coupon-tabs + .coupon-scroll {
  height: calc(100vh - 184rpx - env(safe-area-inset-top));
}

.selection-tip {
  display: flex;
  margin: 24rpx 24rpx 0;
  justify-content: space-between;
  color: $cm-text-secondary;
  font-size: 23rpx;
}

.coupon-list {
  padding: 24rpx;
}

.coupon-card {
  display: flex;
  min-height: 180rpx;
  margin: 24rpx;
  padding: 24rpx;
  align-items: center;
  background: $cm-surface;
  border: 2rpx solid transparent;
  border-radius: $cm-radius-md;
  box-shadow: 0 8rpx 24rpx rgba(18, 38, 63, 0.07);
  box-sizing: border-box;
}

.coupon-list .coupon-card {
  margin: 0 0 20rpx;
}

.coupon-card--none {
  min-height: 112rpx;
  justify-content: space-between;
}

.coupon-card--selected {
  border-color: $cm-primary;
}

.coupon-card--disabled {
  opacity: 0.55;
}

.coupon-card__amount {
  width: 180rpx;
  flex: 0 0 180rpx;
  color: $cm-danger;
  font-size: 42rpx;
  font-weight: 700;
  text-align: center;
}

.coupon-card__currency {
  font-size: 25rpx;
}

.coupon-card__amount > text {
  display: block;
  margin-top: 8rpx;
  color: $cm-text-secondary;
  font-size: 21rpx;
  font-weight: 400;
}

.coupon-card__content {
  min-width: 0;
  flex: 1;
  padding-left: 24rpx;
  border-left: 1rpx dashed $cm-border;
}

.coupon-card__title-row {
  display: flex;
  align-items: center;
}

.coupon-card__name {
  display: block;
  min-width: 0;
  flex: 1;
  overflow: hidden;
  color: $cm-text;
  font-size: 28rpx;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.coupon-card__desc,
.coupon-card__validity,
.coupon-card__reason {
  display: block;
  margin-top: 12rpx;
  color: $cm-text-muted;
  font-size: 21rpx;
  line-height: 32rpx;
}

.coupon-card__reason {
  color: $cm-danger;
}

.coupon-status {
  margin-left: 10rpx;
  padding: 2rpx 10rpx;
  color: $cm-text-secondary;
  background: $cm-page;
  border-radius: $cm-radius-sm;
  font-size: 20rpx;
}

.coupon-status--available { color: $cm-success; background: #edf9f3; }
.coupon-status--locked { color: #b47712; background: #fff6df; }
.coupon-status--used,
.coupon-status--expired { color: $cm-text-muted; }

.coupon-action {
  width: 132rpx;
  height: 64rpx;
  margin: 0 0 0 18rpx;
  padding: 0;
  color: #fff;
  background: $cm-primary;
  border-radius: 32rpx;
  font-size: 24rpx;
  line-height: 64rpx;
}

.coupon-action::after { border: 0; }
.coupon-action[disabled] { color: $cm-text-muted; background: $cm-page; }

.coupon-radio {
  width: 34rpx;
  height: 34rpx;
  margin-left: 20rpx;
  flex: 0 0 34rpx;
  border: 3rpx solid $cm-border;
  border-radius: 50%;
  box-sizing: border-box;
}

.coupon-radio--checked {
  background: $cm-primary;
  border: 9rpx solid $cm-primary-soft;
}

.coupon-bottom-space {
  height: calc(32rpx + env(safe-area-inset-bottom));
}
</style>
