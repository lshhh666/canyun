package com.sky.service.impl;
import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import com.sky.vo.DishVO;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    @Override
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart;
        if (shoppingCartDTO.getDishId() != null) {
            shoppingCart = shoppingCartMapper.selectByUserIdAndDishId(userId, shoppingCartDTO.getDishId());
            if (shoppingCart != null) {
                shoppingCart.setNumber(shoppingCart.getNumber() + 1);
                shoppingCartMapper.updateNumberById(shoppingCart);
            } else {
                DishVO dish = dishMapper.getDishById(shoppingCartDTO.getDishId());
                shoppingCart = ShoppingCart.builder()
                        .number(1)
                        .userId(userId)
                        .dishId(shoppingCartDTO.getDishId())
                        .image(dish.getImage())
                        .name(dish.getName())
                        .dishFlavor(shoppingCartDTO.getDishFlavor())
                        .amount(dish.getPrice())
                        .createTime(LocalDateTime.now())
                        .build();

                shoppingCartMapper.addShoppingCart(shoppingCart);
            }
        } else if (shoppingCartDTO.getSetmealId() != null) {
            shoppingCart = shoppingCartMapper.selectByUserIdAndSetmealId(userId, shoppingCartDTO.getSetmealId());
            if (shoppingCart != null) {
                shoppingCart.setNumber(shoppingCart.getNumber() + 1);
                shoppingCartMapper.updateNumberById(shoppingCart);
            } else {
                SetmealVO setmeal = setmealMapper.getSetmealById(shoppingCartDTO.getSetmealId());
                shoppingCart = ShoppingCart.builder()
                        .number(1)
                        .userId(userId)
                        .setmealId(shoppingCartDTO.getSetmealId())
                        .image(setmeal.getImage())
                        .name(setmeal.getName())
                        .amount(setmeal.getPrice())
                        .createTime(LocalDateTime.now())
                        .build();

                shoppingCartMapper.addShoppingCart(shoppingCart);
            }
        }


    }

    @Override
    public List<ShoppingCart> listShoppingCart() {
        Long userId = BaseContext.getCurrentId();
        return shoppingCartMapper.listShoppingCartByUserId(userId);
    }

    @Override
    public void subShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart;
        if (shoppingCartDTO.getDishId() != null) {
            shoppingCart = shoppingCartMapper.selectByUserIdAndDishId(userId, shoppingCartDTO.getDishId());
            if(shoppingCart != null) {
                if (shoppingCart.getNumber() > 1) {
                    shoppingCart.setNumber(shoppingCart.getNumber() - 1);
                    shoppingCartMapper.updateNumberById(shoppingCart);
                } else {
                    shoppingCartMapper.deleteById(shoppingCart.getId());
                }
            }
        } else if (shoppingCartDTO.getSetmealId() != null) {
            shoppingCart = shoppingCartMapper.selectByUserIdAndSetmealId(userId, shoppingCartDTO.getSetmealId());
            if(shoppingCart != null) {
                if (shoppingCart.getNumber() > 1) {
                    shoppingCart.setNumber(shoppingCart.getNumber() - 1);
                    shoppingCartMapper.updateNumberById(shoppingCart);
                } else {
                    shoppingCartMapper.deleteById(shoppingCart.getId());
                }
            }
        }
    }

    @Override
    public void deleteShoppingCart() {
        Long userId = BaseContext.getCurrentId();
        shoppingCartMapper.deleteShoppingCart(userId);
    }
}

