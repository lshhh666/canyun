package com.sky.service;

import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.result.PageResult;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;

import java.util.List;

public interface SetmealService {
    //新增套餐
    Long insertSetmeal(SetmealDTO setmealDTO);
    //批量删除套餐
    void deleteSetmealByIds(List<Long> ids);
    //根据id查询套餐
    SetmealVO getSetmealById(Long id);

    PageResult SetmealList(SetmealPageQueryDTO setmealPageQueryDTO);

    void updateSetmeal(SetmealDTO setmealDTO);

    void changestatus(Integer status,Long id);

    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    List<Setmeal> list(Setmeal setmeal);

    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    List<DishItemVO> getDishItemById(Long id);

}
