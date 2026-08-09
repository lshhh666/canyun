package com.sky.service.impl;

import com.sky.dto.SetmealDTO;
import com.sky.entity.SetmealDish;
import com.sky.mapper.SetmealdishMapper;
import com.sky.service.SetmealdishService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SetmealdishServiceImpl implements SetmealdishService {
    @Autowired
    private SetmealdishMapper setmealdishMapper;
    @Override
    public void insertSetmeal(SetmealDTO setmealDTO, Long setmealid) {
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if(setmealDishes!=null){
            for(SetmealDish setmealDish:setmealDishes){
                setmealDish.setSetmealId(setmealid);
            }
        }
        setmealdishMapper.insertSetmealdish(setmealDishes);
    }

    @Override
    public void deleteSetmealdishByIds(List<Long> ids) {
        setmealdishMapper.deleteSetmealdishByIds(ids);
    }

    @Override
    public List<SetmealDish> getSetmealdishByDishId(Long dishId) {
        return setmealdishMapper.getSetmealdishByDishId(dishId);
    }

    @Override
    public List<SetmealDish> getSetmealdishById(Long setmealId) {
        return setmealdishMapper.getSetmealdishById(setmealId);
    }

}
