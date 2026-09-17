package com.cloudmeal.service.impl;

import com.cloudmeal.dto.SetmealDTO;
import com.cloudmeal.entity.SetmealDish;
import com.cloudmeal.mapper.SetmealdishMapper;
import com.cloudmeal.service.SetmealdishService;
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
