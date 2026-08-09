package com.sky.service;

import com.sky.dto.SetmealDTO;
import com.sky.entity.SetmealDish;

import java.util.List;

public interface SetmealdishService {
    //新增套餐中的关联的套餐菜品关系
    void insertSetmeal(SetmealDTO setmealDTO, Long setmealid);
    //批量删除套餐
    void deleteSetmealdishByIds(List<Long> ids);
    //根据菜品id查询关联的套餐菜品
    List<SetmealDish> getSetmealdishByDishId(Long dishId);
    //根据套餐id查询关联的菜品
    List<SetmealDish> getSetmealdishById(Long setmealId);

}
