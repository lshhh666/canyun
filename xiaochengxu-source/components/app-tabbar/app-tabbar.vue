<template>
  <view class="app-tabbar">
    <view
      v-for="item in items"
      :key="item.active"
      class="app-tabbar__item"
      :class="{ 'app-tabbar__item--active': active === item.active }"
      @tap="changeTab(item)"
    >
      <uni-icons
        :type="item.icon"
        :color="active === item.active ? '#147ee8' : '#91a0b2'"
        size="22"
      />
      <text class="app-tabbar__text">{{ item.text }}</text>
    </view>
  </view>
</template>

<script>
const items = [
  { active: 'order', text: '点餐', icon: 'home', url: '/pages/index/index' },
  { active: 'orders', text: '订单', icon: 'list', url: '/pages/historyOrder/historyOrder' },
  { active: 'account', text: '我的', icon: 'person', url: '/pages/my/my' }
]

export default {
  name: 'AppTabbar',
  props: {
    active: {
      type: String,
      default: 'order',
      validator(value) {
        return items.some(item => item.active === value)
      }
    }
  },
  data() {
    return {
      items
    }
  },
  methods: {
    changeTab(item) {
      if (item.active === this.active) return
      uni.reLaunch({ url: item.url })
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.app-tabbar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 20;
  display: flex;
  padding: 12rpx 20rpx calc(12rpx + #{$cm-safe-bottom});
  background: $cm-surface;
  border-top: 1rpx solid $cm-border;
  box-shadow: $cm-shadow-float;
}

.app-tabbar__item {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  flex-direction: column;
  color: $cm-text-muted;
}

.app-tabbar__item--active {
  color: $cm-primary;
}

.app-tabbar__text {
  margin-top: 4rpx;
  font-size: 22rpx;
  line-height: 32rpx;
}
</style>
