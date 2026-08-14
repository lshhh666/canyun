<template>
  <view class="payment-shell">
    <cloudmeal-header title="支付订单" show-back @back="goBack" />

    <view class="payment-page">
      <view class="payment-summary">
        <text class="payment-summary__timer">
          {{ timeout ? '订单已超时' : `支付剩余时间 ${rocallTime}` }}
        </text>
        <text class="payment-summary__amount">￥{{ money(orderDataInfo.orderAmount) }}</text>
        <text class="payment-summary__order">
          {{ shopInfo().shopName || '餐云门店' }} · {{ orderDataInfo.orderNumber || '订单' }}
        </text>
      </view>

      <view class="payment-methods">
        <text class="payment-methods__title">支付方式</text>
        <radio-group @change="styleChange">
          <label v-for="(method, index) in payMethodList" :key="method" class="payment-method">
            <view class="payment-method__name">
              <view class="wechat-mark">微</view>
              <text>{{ method }}</text>
            </view>
            <radio
              :value="String(index)"
              color="#147EE8"
              :checked="index === activeRadio"
            />
          </label>
        </radio-group>
      </view>
    </view>

    <view class="payment-submit">
      <button
        class="payment-submit__button"
        :disabled="isPaying"
        :loading="isPaying"
        @click="handleSave"
      >{{ isPaying ? '支付处理中…' : '确认支付' }}</button>
    </view>
  </view>
</template>

<script>
import { mapState } from 'vuex'
import { paymentOrder, cancelOrder } from '@/pages/api/api.js'
import { getErrorMessage } from '../../utils/error-message.js'
import CloudmealHeader from '@/components/cloudmeal-header/cloudmeal-header.vue'

export default {
  components: { CloudmealHeader },
  data() {
    return {
      timeout: false,
      rocallTime: '',
      orderId: null,
      orderDataInfo: {},
      activeRadio: 0,
      payMethodList: ['微信支付'],
      times: null,
      isPaying: false,
    }
  },
  created() {
    this.orderDataInfo = this.orderData() || {}
  },
  mounted() {
    this.runTimeBack()
  },
  onLoad(options) {
    this.orderId = options.orderId
  },
  onUnload() {
    clearTimeout(this.times)
  },
  methods: {
    ...mapState(['orderData', 'shopInfo']),
    money(amount) {
      const value = Number(amount)
      return Number.isFinite(value) ? value.toFixed(2) : '0.00'
    },
    styleChange(event) {
      this.activeRadio = Number(event.detail.value)
    },
    goBack() {
      uni.redirectTo({ url: `/pages/details/index?orderId=${this.orderId}` })
    },
    async handleSave() {
      if (this.isPaying) return false
      this.isPaying = true

      try {
        if (this.timeout) {
          await cancelOrder(this.orderId)
          uni.redirectTo({ url: `/pages/details/index?orderId=${this.orderId}` })
          return false
        }

        const params = {
          orderNumber: this.orderDataInfo.orderNumber,
          payMethod: this.activeRadio === 0 ? 1 : 2,
        }
        const res = await paymentOrder(params)
        if (!res || res.code !== 1) {
          throw new Error((res && res.msg) || '支付发起失败，请重试')
        }

        // Demo payment: the backend has already confirmed and updated the order.
        clearTimeout(this.times)
        await uni.showToast({ title: '支付成功', icon: 'success' })
        uni.redirectTo({ url: `/pages/success/index?orderId=${this.orderId}` })
        return true
      } catch (error) {
        await uni.showToast({ title: getErrorMessage(error, '支付失败，请重试'), icon: 'none' })
        return false
      } finally {
        this.isPaying = false
      }
    },
    runTimeBack() {
      if (!this.orderDataInfo.orderTime) return
      const end = Date.parse(String(this.orderDataInfo.orderTime).replace(/-/g, '/'))
      const remaining = (15 * 60 * 1000) - (Date.now() - end)
      if (remaining <= 0) {
        this.timeout = true
        clearTimeout(this.times)
        return
      }
      const minutes = String(Math.floor(remaining / 60000)).padStart(2, '0')
      const seconds = String(Math.floor((remaining % 60000) / 1000)).padStart(2, '0')
      this.rocallTime = `${minutes}:${seconds}`
      this.times = setTimeout(() => this.runTimeBack(), 1000)
    },
  },
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.payment-shell { min-height: 100vh; color: $cm-text; background: $cm-page; }
.payment-page { padding: 24rpx 24rpx 180rpx; }

.payment-summary {
  display: flex;
  align-items: center;
  flex-direction: column;
  padding: 54rpx 30rpx;
  background: $cm-surface;
  border: 1rpx solid $cm-border;
  border-radius: $cm-radius-md;
}

.payment-summary__timer { color: $cm-text-secondary; font-size: 25rpx; }
.payment-summary__amount { margin-top: 20rpx; color: $cm-text; font-size: 56rpx; font-weight: 700; }
.payment-summary__order { margin-top: 14rpx; color: $cm-text-muted; font-size: 23rpx; }

.payment-methods { margin-top: 20rpx; padding: 28rpx; background: $cm-surface; border: 1rpx solid $cm-border; border-radius: $cm-radius-md; }
.payment-methods__title { display: block; color: $cm-text; font-size: 29rpx; font-weight: 600; }
.payment-method { display: flex; align-items: center; justify-content: space-between; margin-top: 24rpx; padding-top: 24rpx; border-top: 1rpx solid $cm-border; }
.payment-method__name { display: flex; align-items: center; color: $cm-text; font-size: 27rpx; }
.wechat-mark { width: 58rpx; height: 58rpx; margin-right: 18rpx; color: #fff; background: $cm-success; border-radius: 50%; font-size: 22rpx; line-height: 58rpx; text-align: center; }

.payment-submit { position: fixed; right: 0; bottom: 0; left: 0; padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom)); background: $cm-surface; border-top: 1rpx solid $cm-border; }
.payment-submit__button { height: 84rpx; color: #fff; background: $cm-primary; border: 0; border-radius: $cm-radius-sm; font-size: 29rpx; font-weight: 600; line-height: 84rpx; }
.payment-submit__button::after { border: 0; }
.payment-submit__button[disabled] { color: #fff; background: #91bce8; }
</style>
