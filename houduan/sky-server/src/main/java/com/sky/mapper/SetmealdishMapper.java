package com.sky.mapper;

import com.sky.entity.SetmealDish;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SetmealdishMapper {

    void insertSetmealdish(@Param("setmealDishes") List<SetmealDish> setmealDishes);

    void deleteSetmealdishByIds(List<Long> setmealids);

    List<SetmealDish> getSetmealdishById(Long setmealid);

    List<SetmealDish> getSetmealdishByDishId(Long dishId);

}
