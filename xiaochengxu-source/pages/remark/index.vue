<template>
  <view class="remark-page">
    <cloudmeal-header title="订单备注" show-back @back="goBack" />

    <view class="remark-content">
      <view class="remark-card">
        <textarea
          class="remark-input"
          :class="{ 'remark-input--ios': platform === 'ios' }"
          v-model="remark"
          maxlength="50"
          placeholder-class="remark-placeholder"
          placeholder="请输入口味、配送位置等备注"
        />
        <text class="remark-count">{{ remark.length }}/50</text>
      </view>
    </view>

    <view class="remark-footer">
      <button class="remark-save" @click="handleSaveRemark">保存备注</button>
    </view>
  </view>
</template>

<script>
import { mapState, mapMutations } from 'vuex'
import CloudmealHeader from '@/components/cloudmeal-header/cloudmeal-header.vue'

export default {
  components: { CloudmealHeader },
  data () {
    return {
      platform: 'ios',
      remark: '',
      numVal: 0
    }
  },
  computed: {
    ...mapState(['remarkData']),
    getVal () {
      return this.remark
    }
  },
  onLoad () {
    this.platform = uni.getSystemInfoSync().platform
    this.remark = this.remarkData || ''
    this.numVal = this.remark.length
  },
  methods: {
    ...mapMutations(['setRemark']),
    goBack () {
      uni.redirectTo({ url: '/pages/order/index' })
    },
    handleSaveRemark () {
      this.setRemark(this.remark)
      uni.redirectTo({ url: '/pages/order/index' })
    },
    validateTextLength (value) {
      const cnReg = /([\u4e00-\u9fa5]|[\u3000-\u303F]|[\uFF00-\uFF60])/g
      const matches = value.match(cnReg)
      return matches ? matches.length + (value.length - matches.length) * 0.5 : value.length * 0.5
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.remark-page {
  min-height: 100vh;
  background: $cm-page;
  color: $cm-text;
}

.remark-content {
  padding: 24rpx;
}

.remark-card {
  position: relative;
  padding: 28rpx 28rpx 58rpx;
  background: $cm-surface;
  border: 1rpx solid $cm-border;
  border-radius: $cm-radius-md;
}

.remark-input {
  width: 100%;
  height: 300rpx;
  padding: 0;
  color: $cm-text;
  font-size: 28rpx;
  line-height: 44rpx;
}

.remark-input--ios {
  padding-top: 4rpx;
}

.remark-placeholder {
  color: $cm-text-muted;
}

.remark-count {
  position: absolute;
  right: 28rpx;
  bottom: 22rpx;
  color: $cm-text-muted;
  font-size: 24rpx;
}

.remark-footer {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));
  background: $cm-surface;
  border-top: 1rpx solid $cm-border;
}

.remark-save {
  height: 84rpx;
  margin: 0;
  color: #fff;
  background: $cm-primary;
  border: 0;
  border-radius: $cm-radius-sm;
  font-size: 29rpx;
  font-weight: 600;
  line-height: 84rpx;
}

.remark-save::after {
  border: 0;
}
</style>
