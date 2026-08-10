<template>
  <view class="state-panel">
    <text v-if="title" class="state-panel__title">{{ title }}</text>
    <text v-if="description" class="state-panel__description">{{ description }}</text>
    <button
      v-if="actionText"
      class="state-panel__action"
      type="primary"
      @tap="$emit('action')"
    >
      {{ actionText }}
    </button>
  </view>
</template>

<script>
export default {
  name: 'StatePanel',
  props: {
    title: {
      type: String,
      default: ''
    },
    description: {
      type: String,
      default: ''
    },
    actionText: {
      type: String,
      default: ''
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.state-panel {
  display: flex;
  padding: 80rpx 48rpx;
  align-items: center;
  flex-direction: column;
  text-align: center;
}

.state-panel__title {
  color: $cm-text;
  font-size: 32rpx;
  font-weight: 600;
  line-height: 46rpx;
}

.state-panel__description {
  margin-top: 12rpx;
  color: $cm-text-secondary;
  font-size: 26rpx;
  line-height: 40rpx;
}

.state-panel__action {
  min-width: 240rpx;
  margin-top: 32rpx;
  padding: 0 32rpx;
  color: $cm-surface;
  background: $cm-primary;
  border-radius: $cm-radius-md;
  font-size: 28rpx;
  line-height: 80rpx;
}

.state-panel__action::after {
  border: 0;
}

.state-panel__action:active {
  background: $cm-primary-pressed;
}
</style>
