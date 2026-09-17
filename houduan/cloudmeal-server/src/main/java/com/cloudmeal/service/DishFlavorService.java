package com.cloudmeal.service;

import com.cloudmeal.dto.DishDTO;
import com.cloudmeal.entity.DishFlavor;

import java.util.List;

public interface DishFlavorService {
    //新增菜品的口味部分
    void insertDishFlavor(List<DishFlavor> dishFlavors,Long dishId);
}
