<!-- 菜品信息 -->
<template>
  <view class="box order_list">
    <view class="word_text">
      <text class="word_style">{{ shopInfo.shopName }}</text>
    </view>
    <view class="order-type">
      <view class="type_item" v-for="(obj, index) in visibleDishes" :key="index">
        <view class="dish_img">
          <image mode="aspectFill" :src="obj.image" class="dish_img_url" />
        </view>
        <view class="dish_info">
          <view class="dish_name">{{ obj.name }}</view>
          <view class="dish_dishFlavor" v-if="obj.dishFlavor">{{ obj.dishFlavor }}</view>
          <view class="dish_price">×<text class="dish_number">{{ obj.number }}</text></view>
          <view class="dish_active"><text>￥</text>{{ obj.amount.toFixed(2) }}</view>
        </view>
      </view>
      <view class="iconUp" v-if="orderListDataes.length > 3">
        <view class="iconUp__button" @click="expanded = !expanded">
          {{ expanded ? '收起商品' : `展开其余${orderListDataes.length - 3}件商品` }}
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { mapState } from 'vuex'

export default {
  props: {
    orderDataes: {
      type: Array,
      default: () => []
    },
    showDisplay: {
      type: Boolean,
      default: false
    },
    orderListDataes: {
      type: Array,
      default: () => []
    },
    orderDishNumber: {
      type: Number,
      default: 0
    },
    orderDishPrice: {
      type: Number,
      default: 0
    }
  },
  data () {
    return {
      expanded: this.showDisplay
    }
  },
  computed: {
    ...mapState(['deliveryFee', 'shopInfo']),
    visibleDishes () {
      return this.expanded ? this.orderListDataes : this.orderDataes
    }
  }
}
</script>

<style src="./../style.scss" lang="scss" scoped></style>
