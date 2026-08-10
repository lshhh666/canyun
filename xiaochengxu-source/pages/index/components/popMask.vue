<template>
  <view class="spec-sheet">
    <view class="sheet-handle"></view>
    <view class="sheet-header">
      <view>
        <view class="sheet-title">{{ moreNormDishdata.name }}</view>
        <view class="sheet-subtitle">请选择口味或规格</view>
      </view>
      <view class="sheet-close" aria-label="关闭" @click="closeMoreNorm(moreNormDishdata)">×</view>
    </view>

    <scroll-view class="spec-list" scroll-y="true">
      <view class="spec-group" v-for="(obj, index) in moreNormdata" :key="index">
        <view class="spec-name">{{ obj.name }}</view>
        <view class="spec-options">
          <view
            class="spec-option"
            :class="{ selected: flavorDataes.findIndex(it => item === it) !== -1 }"
            v-for="(item, ind) in obj.value"
            :key="ind"
            @click="checkMoreNormPop(obj.value, item)"
          >
            {{ item }}
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="sheet-footer">
      <view class="spec-price"><text>￥</text>{{ displayPrice }}</view>
      <view class="primary-action" @click="addShop(moreNormDishdata, '普通')">加入购物车</view>
    </view>
  </view>
</template>

<script>
export default {
  props: {
    moreNormDishdata: {
      type: Object,
      default: () => ({})
    },
    moreNormdata: {
      type: Array,
      default: () => []
    },
    flavorDataes: {
      type: Array,
      default: () => []
    }
  },
  computed: {
    displayPrice() {
      return Number(this.moreNormDishdata.price || 0).toFixed(2)
    }
  },
  methods: {
    checkMoreNormPop(obj, item) {
      this.$emit("checkMoreNormPop", { obj: obj, item: item })
    },
    addShop(obj) {
      this.$emit("addShop", obj)
    },
    closeMoreNorm(obj) {
      this.$emit("closeMoreNorm", obj)
    }
  }
}
</script>

<style lang="scss" scoped>
.spec-sheet {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  max-height: 78vh;
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
.sheet-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sheet-title {
  color: #12263f;
  font-size: 34rpx;
  font-weight: 600;
  line-height: 48rpx;
}

.sheet-subtitle {
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

.spec-list {
  max-height: 50vh;
  margin-top: 16rpx;
}

.spec-group {
  padding: 18rpx 0 8rpx;
}

.spec-name {
  color: #12263f;
  font-size: 27rpx;
  font-weight: 600;
  line-height: 40rpx;
}

.spec-options {
  display: flex;
  margin: 4rpx -8rpx 0;
  flex-wrap: wrap;
}

.spec-option {
  min-width: 112rpx;
  margin: 12rpx 8rpx;
  padding: 0 22rpx;
  color: #526377;
  background: #ffffff;
  border: 1rpx solid #cbd5df;
  border-radius: 8rpx;
  font-size: 25rpx;
  line-height: 64rpx;
  text-align: center;
  box-sizing: border-box;
}

.spec-option.selected {
  color: #147ee8;
  background: #eef6ff;
  border-color: #147ee8;
}

.sheet-footer {
  min-height: 96rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid #e8edf2;
}

.spec-price {
  color: #12263f;
  font-size: 40rpx;
  font-weight: 600;
}

.spec-price text {
  font-size: 24rpx;
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
