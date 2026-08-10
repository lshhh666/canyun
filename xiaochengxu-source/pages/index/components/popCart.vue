<template>
  <view class="cart-sheet" @click.stop>
    <view class="sheet-handle"></view>
    <view class="sheet-header">
      <view>
        <view class="sheet-title">购物车</view>
        <view class="sheet-subtitle">已选商品可继续调整数量</view>
      </view>
      <view class="clear-action" @click.stop="clearCardOrder()">清空购物车</view>
    </view>

    <scroll-view class="cart-list" scroll-y="true">
      <view class="cart-group" v-for="(item, ind) in orderAndUserInfo" :key="ind">
        <view class="cart-item" v-for="(obj, index) in item.dishList" :key="index">
          <image mode="aspectFill" :src="obj.image" class="dish-image"></image>
          <view class="dish-info">
            <view class="dish-name">{{ obj.name }}</view>
            <view class="dish-flavor" v-if="obj.dishFlavor">{{ obj.dishFlavor }}</view>
            <view class="dish-row">
              <view class="dish-price"><text>￥</text>{{ obj.amount }}</view>
              <view class="quantity-control">
                <view
                  v-if="obj.number && obj.number > 0"
                  class="quantity-button quantity-button--minus"
                  @click.stop="redDishAction(obj, '购物车')"
                >
                  −
                </view>
                <text v-if="obj.number && obj.number > 0" class="dish-number">{{ obj.number }}</text>
                <view class="quantity-button quantity-button--add" @click.stop="addDishAction(obj, '购物车')">
                  ＋
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>
      <view class="list-space"></view>
    </scroll-view>
  </view>
</template>

<script>
export default {
  props: {
    orderAndUserInfo: {
      type: Array,
      default: () => []
    },
    openOrderCartList: {
      type: Boolean,
      default: false
    }
  },
  methods: {
    clearCardOrder() {
      this.$emit("clearCardOrder")
    },
    addDishAction(obj, item) {
      this.$emit("addDishAction", { obj: obj, item: item })
    },
    redDishAction(obj, item) {
      this.$emit("redDishAction", { obj: obj, item: item })
    }
  }
}
</script>

<style lang="scss" scoped>
.cart-sheet {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  max-height: 72vh;
  padding: 12rpx 28rpx calc(28rpx + env(safe-area-inset-bottom));
  overflow: hidden;
  background: #ffffff;
  border-radius: 16rpx 16rpx 0 0;
  box-sizing: border-box;
}

.sheet-handle {
  width: 72rpx;
  height: 6rpx;
  margin: 0 auto 18rpx;
  background: #d9e0e8;
  border-radius: 3rpx;
}

.sheet-header,
.cart-item,
.dish-row,
.quantity-control {
  display: flex;
  align-items: center;
}

.sheet-header,
.dish-row {
  justify-content: space-between;
}

.sheet-title {
  color: #12263f;
  font-size: 34rpx;
  font-weight: 600;
  line-height: 48rpx;
}

.sheet-subtitle,
.dish-flavor {
  color: #748396;
  font-size: 24rpx;
  line-height: 36rpx;
}

.clear-action {
  padding: 8rpx 0 8rpx 20rpx;
  color: #d94b4b;
  font-size: 25rpx;
}

.cart-list {
  max-height: 56vh;
  margin-top: 14rpx;
}

.cart-item {
  padding: 20rpx 0;
  border-bottom: 1rpx solid #e8edf2;
}

.dish-image {
  width: 120rpx;
  height: 120rpx;
  flex: 0 0 120rpx;
  border-radius: 12rpx;
  background: #eef2f6;
}

.dish-info {
  min-width: 0;
  margin-left: 20rpx;
  flex: 1;
}

.dish-name {
  overflow: hidden;
  color: #12263f;
  font-size: 28rpx;
  font-weight: 600;
  line-height: 40rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dish-flavor {
  margin-top: 2rpx;
}

.dish-row {
  margin-top: 14rpx;
}

.dish-price {
  color: #12263f;
  font-size: 30rpx;
  font-weight: 600;
}

.dish-price text {
  font-size: 22rpx;
}

.quantity-button {
  display: flex;
  width: 52rpx;
  height: 52rpx;
  align-items: center;
  justify-content: center;
  color: #147ee8;
  background: #ffffff;
  border: 1rpx solid #147ee8;
  border-radius: 50%;
  box-sizing: border-box;
  font-size: 32rpx;
}

.quantity-button--add {
  color: #ffffff;
  background: #147ee8;
}

.quantity-button--minus {
  color: #d94b4b;
  border-color: #d94b4b;
}

.dish-number {
  min-width: 48rpx;
  color: #12263f;
  font-size: 27rpx;
  text-align: center;
}

.list-space {
  height: 20rpx;
}
</style>
