<template>
  <view class="ordering-page" :class="{ 'ordering-page--closed': shopStatus !== 1 }">
    <cloudmeal-header
      title="餐云点餐"
      :subtitle="shopAddressText"
      :status="shopStatusText"
    />

    <view class="store-contact" @click="handlePhone('bottom')">
      <view class="store-contact__fee">
        <text class="store-contact__label">配送费</text>
        <text>锟{ deliveryFeeText }}</text>
      </view>
      <view class="store-contact__action">
        <text>{{ shopAddressText }}</text>
        <text class="store-contact__phone">联系门店</text>
      </view>
    </view>

    <view class="menu-layout">
      <view class="type_list">
        <scroll-view
          scroll-y
          scroll-with-animation
          class="u-tab-view menu-scroll-view"
          :scroll-top="scrollTop + 100"
          :scroll-into-view="itemId"
        >
          <view
            class="type_item"
            id="target"
            :class="[typeIndex == index ? 'active' : '']"
            v-for="(item, index) in typeListData"
            :key="index"
            @tap.stop="swichMenu(item, index)"
          >
            <view class="item" :class="item.name.length > 5 ? 'allLine' : ''">
              {{ item.name }}
            </view>
          </view>
          <view class="seize_seat"></view>
        </scroll-view>
      </view>

      <state-panel
        v-if="menuLoadFailed"
        class="menu-empty"
        title="鑿滃崟鍔犺浇澶辫触"
        description="璇锋鏌ョ綉缁滃悗閲嶆柊鍔犺浇"
        actionText="閲嶆柊鍔犺浇"
        @action="reloadMenu"
      />
      <scroll-view
        class="vegetable_order_list"
        scroll-y="true"
        scroll-top="0rpx"
        v-else-if="dishListItems && dishListItems.length > 0"
      >
        <view class="type_item" v-for="(item, index) in dishListItems" :key="index">
          <view class="dish_img" @click="openDetailHandle(item)">
            <image mode="aspectFill" :src="item.image" class="dish_img_url"></image>
          </view>
          <view class="dish_info">
            <view class="dish_name" @click="openDetailHandle(item)">{{ item.name }}</view>
            <view class="dish_label" @click="openDetailHandle(item)">
              {{ item.description || item.name }}
            </view>
            <view class="dish_price">
              <text class="ico">￥</text>{{ item.price.toFixed(2) }}
            </view>
            <view class="dish_active" v-if="!item.flavors || item.flavors.length === 0">
              <view
                v-if="item.dishNumber >= 1"
                class="quantity-button quantity-button--minus"
                @click="redDishAction(item, '普通')"
              >
                −
              </view>
              <text v-if="item.dishNumber > 0" class="dish_number">{{ item.dishNumber }}</text>
              <view class="quantity-button quantity-button--add" @click="addDishAction(item, '普通')">
                ＋
              </view>
            </view>
            <view class="dish_active_btn" v-else>
              <view class="check_but" @click="moreNormDataesHandle(item)">选择规格</view>
            </view>
          </view>
        </view>
        <view class="seize_seat"></view>
      </scroll-view>

      <state-panel
        v-else-if="typeListData.length > 0"
        class="menu-empty"
        title="当前分类暂无菜品"
        description="可以看看其他分类"
      />
    </view>

    <view class="cart-bar" :class="{ disabled: orderListData().length === 0 || shopStatus !== 1 }">
      <view
        class="cart-summary"
        @click="openOrderCartList = orderListData().length > 0 && !openOrderCartList"
      >
        <view class="cart-icon" aria-label="购物车">购</view>
        <text v-if="orderDishNumber > 0" class="cart-count">{{ orderDishNumber }}</text>
        <text class="cart-price">￥{{ orderDishPrice.toFixed(2) }}</text>
      </view>
      <button
        class="cart-submit"
        :disabled="orderListData().length === 0 || shopStatus !== 1"
        @click="goOrder()"
      >
        {{ shopStatus === null ? '状态加载中' : shopStatus !== 1 ? '门店休息中' : orderListData().length === 0 ? '请先选购商品' : '去结算' }}
      </button>
    </view>

    <app-tabbar active="order" />

    <view class="pop_mask" v-show="openMoreNormPop">
      <popMask
        :moreNormDishdata="moreNormDishdata"
        :moreNormdata="moreNormdata"
        :flavorDataes="flavorDataes"
        @checkMoreNormPop="checkMoreNormPop"
        @addShop="addShop"
        @closeMoreNorm="closeMoreNorm"
      />
    </view>

    <view class="pop_mask" v-show="openDetailPop">
      <dishDetail
        :dishDetailes="dishDetailes"
        :openDetailPop="openDetailPop"
        :dishMealData="dishMealData"
        @redDishAction="redDishAction"
        @addDishAction="addDishAction"
        @moreNormDataesHandle="moreNormDataesHandle"
        @dishClose="dishClose"
      />
    </view>

    <view class="pop_mask" v-show="openOrderCartList" @click="openOrderCartList = false">
      <popCart
        :openOrderCartList="openOrderCartList"
        :orderAndUserInfo="orderAndUserInfo"
        @clearCardOrder="clearCardOrder"
        @addDishAction="addDishAction"
        @redDishAction="redDishAction"
      />
    </view>

    <view class="pop_mask loading-mask" v-if="menuLoading">
      <view class="loading-card">菜单加载中…</view>
    </view>

    <phone ref="phone" :phoneData="phoneData" @closePopup="closePopup"></phone>
  </view>
</template>

<script src="./index.js"></script>
<style src="./style.scss" lang="scss" scoped></style>
<style scoped>
/* #ifdef MP-WEIXIN || APP-PLUS */
::v-deep ::-webkit-scrollbar {
  display: none !important;
  width: 0 !important;
  height: 0 !important;
  -webkit-appearance: none;
  background: transparent;
  color: transparent;
}
/* #endif */
</style>
