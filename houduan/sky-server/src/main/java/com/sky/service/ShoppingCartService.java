package com.sky.service;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;

import java.util.List;

public interface ShoppingCartService {
    void addShoppingCart(ShoppingCartDTO shoppingCartDTO);
    //查看购物车
    List<ShoppingCart> listShoppingCart();
    //删除购物车的一个商品
    void subShoppingCart(ShoppingCartDTO shoppingCartDTO);
    //清空购物车
    void deleteShoppingCart();
}
