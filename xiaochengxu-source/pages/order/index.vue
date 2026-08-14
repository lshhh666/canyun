<!-- 确认订单 -->
<template>
  <view class="checkout-shell">
    <cloudmeal-header title="确认订单" show-back @back="goBack" />

    <scroll-view class="checkout-page" scroll-y @touchstart="touchstart">
      <address-pop
        :address="address"
        :tagLabel="tagLabel"
        :addressLabel="addressLabel"
        :nickName="nickName"
        :phoneNumber="phoneNumber"
        :arrivalTime="arrivalTime"
        :popleft="popleft"
        :weeks="weeks"
        :newDateData="newDateData"
        :tabIndex="tabIndex"
        :selectValue="selectValue"
        @change="change"
        @goAddress="goAddress"
        @dateChange="dateChange"
        @timeClick="timeClick"
      />

      <dish-detail
        :orderDataes="orderDataes"
        :showDisplay="showDisplay"
        :orderDishNumber="orderDishNumber"
        :orderListDataes="orderListDataes"
        :orderDishPrice="orderDishPrice"
      />

      <dish-info
        ref="dishinfo"
        :remark="remark"
        :tablewareData="tablewareData"
        :radioGroup="radioGroup"
        :activeRadio="activeRadio"
        :baseData="baseData"
        @goRemark="goRemark"
        @openPopuos="openPopuos"
        @change="change"
        @closePopup="closePopup"
        @handlePiker="handlePiker"
        @changeCont="changeCont"
        @handleRadio="handleRadio"
      />

      <view class="checkout-card fee-summary">
        <view class="section-title">费用明细</view>
        <view class="fee-row">
          <text>商品金额</text>
          <text>￥{{ dishAmount.toFixed(2) }}</text>
        </view>
        <view class="fee-row">
          <text>打包费</text>
          <text>￥{{ packFeeAmount.toFixed(2) }}</text>
        </view>
        <view class="fee-row">
          <text>配送费</text>
          <text>￥{{ deliveryFeeAmount.toFixed(2) }}</text>
        </view>
        <view v-if="previewState === 'loading'" class="preview-state">正在计算订单金额...</view>
        <view v-else-if="previewState === 'error'" class="preview-state preview-state--error">
          <text>订单金额获取失败</text>
          <text class="preview-retry" @click="loadPreview">重新计算</text>
        </view>
      </view>
    </scroll-view>

    <view class="checkout-submit">
      <view class="checkout-total">
        <text class="checkout-total__label">合计</text>
        <text class="checkout-total__amount">￥{{ totalAmount.toFixed(2) }}</text>
      </view>
      <button
        class="checkout-submit__button"
        :disabled="isHandlePy || previewState !== 'ready'"
        :loading="isHandlePy"
        @click="payOrderHandle()"
      >提交订单</button>
    </view>
  </view>
</template>

<script src="./index.js"></script>
<style src="./style.scss" lang="scss" scoped></style>
