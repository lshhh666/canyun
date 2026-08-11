<template>
  <view class="detail-shell">
    <cloudmeal-header title="订单详情" show-back @back="goBack" />

    <scroll-view class="detail-page" scroll-y>
      <status
        ref="status"
        :timeout="timeout"
        :order-details-data="orderDetailsData"
        :rocall-time="rocallTime"
        @statusWord="statusWord"
        @paymentTime="paymentTime"
        @handlePay="handlePay"
        @handleReminder="handleReminder"
        @handleRefund="handleRefund"
        @handleCancel="handleCancel"
        @oneMoreOrder="oneMoreOrder"
      />

      <order-detail
        :order-dataes="orderDataes"
        :order-details-data="orderDetailsData"
        :show-display="showDisplay"
        @toggle="showDisplay = !showDisplay"
      />

      <view class="contact-card">
        <button class="contact-button" @click="handlePhone('bottom', orderDetailsData.shopTelephone)">
          联系商家
        </button>
        <button
          v-if="[4, 5].includes(Number(orderDetailsData.status)) && orderDetailsData.courierTelephone"
          class="contact-button"
          @click="handlePhone('bottom', orderDetailsData.courierTelephone)"
        >联系骑手</button>
      </view>

      <delivery-info :order-details-data="orderDetailsData" />
      <order-info :order-details-data="orderDetailsData" />
    </scroll-view>

    <uni-popup ref="commonPopup" class="detail-popup">
      <view class="popup-content">
        <view class="popup-content__text">{{ textTip }}</view>
        <view v-if="showConfirm" class="popup-content__actions">
          <view @click="closePopupInfo">知道了</view>
        </view>
        <view v-else class="popup-content__actions">
          <view @click="closePopupInfo">先等等</view>
          <view @click="handlePhone('bottom', orderDetailsData.shopTelephone)">联系商家</view>
        </view>
      </view>
    </uni-popup>

    <uni-popup ref="phone" class="phone-popup">
      <view class="phone-sheet">
        <view class="phone-sheet__number">{{ phone }}</view>
        <view class="phone-sheet__action" @click="call">呼叫</view>
        <view class="phone-sheet__cancel" @click="closePopup">取消</view>
      </view>
    </uni-popup>
  </view>
</template>

<script src="./index.js"></script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.detail-shell {
  min-height: 100vh;
  color: $cm-text;
  background: $cm-page;
}

.detail-page {
  height: calc(100vh - 96rpx - env(safe-area-inset-top));
  padding: 24rpx 24rpx calc(32rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.contact-card {
  display: flex;
  margin-top: 20rpx;
  padding: 12rpx;
  background: $cm-surface;
  border: 1rpx solid $cm-border;
  border-radius: $cm-radius-md;
}

.contact-button {
  height: 70rpx;
  flex: 1;
  margin: 0;
  color: $cm-primary;
  background: $cm-surface;
  border: 0;
  font-size: 26rpx;
  line-height: 70rpx;
}

.contact-button + .contact-button { border-left: 1rpx solid $cm-border; }
.contact-button::after { border: 0; }

.popup-content {
  overflow: hidden;
  width: 520rpx;
  background: $cm-surface;
  border-radius: $cm-radius-md;
}

.popup-content__text { padding: 54rpx 40rpx; color: $cm-text; font-size: 28rpx; text-align: center; }
.popup-content__actions { display: flex; color: $cm-text-secondary; border-top: 1rpx solid $cm-border; }
.popup-content__actions view { flex: 1; padding: 24rpx; text-align: center; }
.popup-content__actions view + view { color: $cm-primary; border-left: 1rpx solid $cm-border; }

.phone-sheet {
  padding-bottom: env(safe-area-inset-bottom);
  background: $cm-surface;
  border-radius: 24rpx 24rpx 0 0;
  text-align: center;
}

.phone-sheet view { padding: 34rpx; font-size: 29rpx; }
.phone-sheet__number { color: $cm-text-secondary; border-bottom: 1rpx solid $cm-border; }
.phone-sheet__action { color: $cm-primary; }
.phone-sheet__cancel { color: $cm-text-secondary; background: $cm-page; }
</style>
