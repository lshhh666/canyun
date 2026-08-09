package com.sky.service;


import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.vo.DishVO;

import java.util.List;

public interface DishService {
    //新增菜品
    Long insertDish(DishDTO dishDTO);
    //菜品分页查询
    PageResult pageDish(DishPageQueryDTO dishPageQueryDTO);
    //批量删除菜品
    void deleteDishByIds(List<Long> dishIds);
    //根据id查询菜品
    DishVO getDishById(Long id);

    void updateDish(DishDTO dishDTO);

    List<Dish> getDishByCategoryId(Long categoryId);

    void statusUpdateDish(Dish dish);

    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    List<DishVO> listWithFlavor(Dish dish);

}
