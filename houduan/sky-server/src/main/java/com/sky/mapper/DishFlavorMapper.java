package com.sky.mapper;

import com.sky.entity.DishFlavor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DishFlavorMapper {
    //新增菜品种的口味
    void insertDishFlavor(@Param("dishFlavors") List<DishFlavor> dishFlavors);
    //批量删除菜品口味
    void deleteByDishIds(@Param("dishIds")List<Long> dishIds);

    List<DishFlavor> getByDishId(Long dishId);
}
