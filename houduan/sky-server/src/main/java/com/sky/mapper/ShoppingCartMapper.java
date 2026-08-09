package com.sky.mapper;

import com.sky.annotation.AutoFill;
import com.sky.entity.ShoppingCart;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {

    ShoppingCart selectByUserIdAndDishId(Long userId, Long dishId);

    void updateNumberById(ShoppingCart shoppingCart);

    void addShoppingCart(ShoppingCart shoppingCart);

    ShoppingCart selectByUserIdAndSetmealId(Long userId, Long setmealId);
    //查看购物车
    @Select("select * from shopping_cart where user_id=#{userId} order by create_time desc")
    List<ShoppingCart> listShoppingCartByUserId(Long userId);
    //删除购物车一条数据
    void deleteById(Long id);
    //清空购物车
    @Delete("delete from shopping_cart where user_id=#{userId}")
    void deleteShoppingCart(Long userId);
}
