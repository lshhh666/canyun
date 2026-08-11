<template>
  <view class="recent-orders">
    <view
      v-for="item in recentOrdersList"
      :key="item.id"
      class="order-card"
    >
      <view class="order-card__header">
        <text class="order-card__time">{{ item.orderTime }}</text>
        <text class="order-card__status">{{ statusWord(item.status) }}</text>
      </view>

      <view class="order-card__body" @click="goDetail(item.id)">
        <view class="dish-preview">
          <image
            v-if="item.orderDetailList && item.orderDetailList.length"
            class="dish-preview__image"
            :src="item.orderDetailList[0].image"
            mode="aspectFill"
          />
          <view class="dish-preview__copy">
            <text class="dish-preview__name">{{ dishSummary(item.orderDetailList) }}</text>
            <text class="dish-preview__count">共 {{ numes(item.orderDetailList).count }} 件</text>
          </view>
        </view>
        <view class="order-card__total">
          <text>￥{{ money(item.amount) }}</text>
          <uni-icons type="right" color="#91a0b2" size="16" />
        </view>
      </view>

      <view class="order-card__actions">
        <button class="order-card__button" @click="oneOrderFun(item.id)">再来一单</button>
        <button
          v-if="Number(item.status) === 1 && getOvertime(item.orderTime) > 0"
          class="order-card__button order-card__button--primary"
          @click="goDetail(item.id)"
        >去支付</button>
      </view>
    </view>
    <text v-if="loadingText" class="recent-orders__tip">{{ loadingText }}</text>
  </view>
</template>

<script>
import { statusWord, getOvertime } from '@/utils/index.js'

export default {
  name: 'OrderList',
  props: {
    scrollH: {
      type: Number,
      default: 0
    },
    loading: {
      type: Boolean,
      default: false
    },
    loadingText: {
      type: String,
      default: ''
    },
    recentOrdersList: {
      type: Array,
      default: () => []
    }
  },
  methods: {
    lower() {
      this.$emit('lower')
    },
    goDetail(id) {
      this.$emit('goDetail', id)
    },
    oneOrderFun(id) {
      this.$emit('oneOrderFun', id)
    },
    numes(list = []) {
      const count = list.reduce((sum, item) => sum + Number(item.number || 0), 0)
      return { count }
    },
    dishSummary(list = []) {
      if (!list.length) return '订单餐品'
      const names = list.map(item => item.name).filter(Boolean)
      return names.slice(0, 2).join('、') || '订单餐品'
    },
    money(value) {
      return Number(value || 0).toFixed(2)
    },
    getOvertime(time) {
      return getOvertime(time)
    },
    statusWord(status) {
      return statusWord(Number(status))
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.recent-orders {
  padding: 0 24rpx;
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
.dish-preview,
.order-card__total,
.order-card__actions {
  display: flex;
  align-items: center;
}

.order-card__header {
  justify-content: space-between;
  padding-bottom: 20rpx;
  border-bottom: 1rpx solid $cm-border;
}

.order-card__time {
  color: $cm-text-secondary;
  font-size: 24rpx;
}

.order-card__status {
  color: $cm-primary;
  font-size: 25rpx;
  font-weight: 600;
}

.order-card__body {
  justify-content: space-between;
  padding: 24rpx 0;
}

.dish-preview {
  min-width: 0;
  flex: 1;
}

.dish-preview__image {
  width: 96rpx;
  height: 96rpx;
  flex: 0 0 96rpx;
  margin-right: 20rpx;
  background: $cm-page;
  border-radius: $cm-radius-sm;
}

.dish-preview__copy {
  min-width: 0;
}

.dish-preview__name,
.dish-preview__count {
  display: block;
}

.dish-preview__name {
  max-width: 300rpx;
  overflow: hidden;
  color: $cm-text;
  font-size: 27rpx;
  line-height: 40rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dish-preview__count {
  margin-top: 6rpx;
  color: $cm-text-secondary;
  font-size: 23rpx;
}

.order-card__total {
  margin-left: 20rpx;
  color: $cm-text;
  font-size: 28rpx;
  font-weight: 600;
}

.order-card__actions {
  justify-content: flex-end;
  gap: 16rpx;
}

.order-card__button {
  min-width: 168rpx;
  height: 68rpx;
  margin: 0;
  padding: 0 24rpx;
  color: $cm-primary;
  background: $cm-surface;
  border: 1rpx solid $cm-primary;
  border-radius: 34rpx;
  font-size: 25rpx;
  line-height: 66rpx;
}

.order-card__button::after {
  border: 0;
}

.order-card__button--primary {
  color: $cm-surface;
  background: $cm-primary;
}

.recent-orders__tip {
  display: block;
  padding: 16rpx 0;
  color: $cm-text-muted;
  font-size: 23rpx;
  text-align: center;
}
</style>
