package com.sky.service;

import com.sky.dto.DishDTO;
import com.sky.entity.DishFlavor;

import java.util.List;

public interface DishFlavorService {
    //新增菜品的口味部分
    void insertDishFlavor(List<DishFlavor> dishFlavors,Long dishId);
}
