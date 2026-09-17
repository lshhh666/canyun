package com.cloudmeal.service;

import com.cloudmeal.dto.SetmealDTO;
import com.cloudmeal.dto.SetmealPageQueryDTO;
import com.cloudmeal.entity.Setmeal;
import com.cloudmeal.result.PageResult;
import com.cloudmeal.vo.DishItemVO;
import com.cloudmeal.vo.SetmealVO;

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
