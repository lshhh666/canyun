<template>
  <view class="dish-sheet">
    <view class="sheet-handle"></view>
    <view class="sheet-header">
      <view>
        <view class="sheet-title">{{ dishDetailes.name }}</view>
        <view class="sheet-subtitle">{{ dishDetailes.type == 1 ? '菜品详情' : '套餐内容' }}</view>
      </view>
      <view class="sheet-close" aria-label="关闭" @click="dishClose">×</view>
    </view>

    <scroll-view class="sheet-content" scroll-y="true">
      <template v-if="dishDetailes.type == 1">
        <image mode="aspectFill" class="detail-image" :src="dishDetailes.image"></image>
        <view v-if="dishDetailes.description" class="detail-desc">{{ dishDetailes.description }}</view>
      </template>

      <view v-else class="meal-list">
        <view class="meal-item" v-for="(item, index) in dishMealData" :key="index">
          <image class="meal-image" :src="item.image" mode="aspectFill"></image>
          <view class="meal-info">
            <view class="meal-name">{{ item.name }} <text>×{{ item.copies }}</text></view>
            <view class="meal-desc">{{ item.description }}</view>
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="sheet-footer">
      <view class="detail-price"><text>￥</text>{{ displayPrice }}</view>
      <view
        class="quantity-control"
        v-if="hasNoFlavors && dishDetailes.dishNumber > 0"
      >
        <view class="quantity-button quantity-button--minus" @click="redDishAction(dishDetailes, '普通')">−</view>
        <text class="dish-number">{{ dishDetailes.dishNumber }}</text>
        <view class="quantity-button quantity-button--add" @click="addDishAction(dishDetailes, '普通')">＋</view>
      </view>
      <view v-else-if="!hasNoFlavors" class="primary-action" @click="moreNormDataesHandle(dishDetailes)">
        选择规格
      </view>
      <view v-else class="primary-action" @click="addDishAction(dishDetailes, '普通')">加入购物车</view>
    </view>
  </view>
</template>

<script>
export default {
  props: {
    dishDetailes: {
      type: Object,
      default: () => ({})
    },
    openDetailPop: {
      type: Boolean,
      default: false
    },
    dishMealData: {
      type: Array,
      default: () => []
    }
  },
  computed: {
    hasNoFlavors() {
      return !this.dishDetailes.flavors || this.dishDetailes.flavors.length === 0
    },
    displayPrice() {
      const price = Number(this.dishDetailes.price || 0)
      return price.toFixed(2)
    }
  },
  methods: {
    addDishAction(obj, item) {
      this.$emit("addDishAction", { obj: obj, item: item })
    },
    redDishAction(obj, item) {
      this.$emit("redDishAction", { obj: obj, item: item })
    },
    moreNormDataesHandle(obj) {
      this.$emit("moreNormDataesHandle", obj)
    },
    dishClose() {
      this.$emit("dishClose")
    }
  }
}
</script>

<style lang="scss" scoped>
.dish-sheet {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  max-height: 82vh;
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
.sheet-footer,
.meal-item,
.quantity-control {
  display: flex;
  align-items: center;
}

.sheet-header {
  justify-content: space-between;
}

.sheet-title {
  color: #12263f;
  font-size: 34rpx;
  font-weight: 600;
  line-height: 48rpx;
}

.sheet-subtitle,
.detail-desc,
.meal-desc {
  color: #748396;
  font-size: 24rpx;
  line-height: 36rpx;
}

.sheet-close {
  width: 56rpx;
  color: #647489;
  font-size: 46rpx;
  line-height: 56rpx;
  text-align: center;
}

.sheet-content {
  max-height: 54vh;
  margin-top: 22rpx;
}

.detail-image {
  display: block;
  width: 100%;
  height: 360rpx;
  border-radius: 16rpx;
  background: #eef2f6;
}

.detail-desc {
  padding: 20rpx 0 8rpx;
}

.meal-item {
  padding: 18rpx 0;
  border-bottom: 1rpx solid #e8edf2;
}

.meal-image {
  width: 112rpx;
  height: 112rpx;
  flex: 0 0 112rpx;
  border-radius: 12rpx;
  background: #eef2f6;
}

.meal-info {
  min-width: 0;
  margin-left: 20rpx;
}

.meal-name {
  color: #12263f;
  font-size: 28rpx;
  font-weight: 600;
  line-height: 40rpx;
}

.meal-name text {
  margin-left: 8rpx;
  color: #748396;
  font-weight: 400;
}

.sheet-footer {
  min-height: 96rpx;
  padding-top: 20rpx;
  justify-content: space-between;
  border-top: 1rpx solid #e8edf2;
}

.detail-price {
  color: #12263f;
  font-size: 40rpx;
  font-weight: 600;
}

.detail-price text {
  font-size: 24rpx;
}

.quantity-button {
  display: flex;
  width: 56rpx;
  height: 56rpx;
  align-items: center;
  justify-content: center;
  color: #147ee8;
  background: #ffffff;
  border: 1rpx solid #147ee8;
  border-radius: 50%;
  box-sizing: border-box;
  font-size: 34rpx;
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
  min-width: 52rpx;
  color: #12263f;
  font-size: 28rpx;
  text-align: center;
}

.primary-action {
  min-width: 204rpx;
  padding: 0 28rpx;
  color: #ffffff;
  background: #147ee8;
  border-radius: 8rpx;
  font-size: 27rpx;
  line-height: 72rpx;
  text-align: center;
  box-sizing: border-box;
}
</style>
