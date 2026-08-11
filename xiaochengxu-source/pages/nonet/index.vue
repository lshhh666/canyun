<template>
  <view class="network-page">
    <state-panel
      title="网络连接失败"
      description="请检查网络后重新加载"
      action-text="重新加载"
      @action="retry"
    />
  </view>
</template>

<script>
import StatePanel from '@/components/state-panel/state-panel.vue'

export default {
  components: { StatePanel },
  methods: {
    retry() {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        uni.navigateBack({ delta: 1 })
        return
      }
      uni.reLaunch({ url: '/pages/index/index' })
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.network-page {
  display: flex;
  min-height: 100vh;
  padding: calc(140rpx + env(safe-area-inset-top)) 32rpx calc(48rpx + env(safe-area-inset-bottom));
  align-items: flex-start;
  justify-content: center;
  box-sizing: border-box;
  background: $cm-page;
}

.network-page ::v-deep .state-panel {
  width: 100%;
  background: $cm-surface;
  border: 1rpx solid $cm-border;
  border-radius: $cm-radius-md;
}
</style>
