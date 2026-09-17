package com.cloudmeal.service.impl;

import com.cloudmeal.dto.DishDTO;
import com.cloudmeal.entity.DishFlavor;
import com.cloudmeal.mapper.DishFlavorMapper;
import com.cloudmeal.service.DishFlavorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DishFlavorServiceImpl implements DishFlavorService {
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    @Override
    public void insertDishFlavor(List<DishFlavor> dishFlavors,Long dishId) {
            if(dishFlavors!=null&&dishFlavors.size()>0){
                for(DishFlavor dishFlavor:dishFlavors){
                    dishFlavor.setDishId(dishId);
                }
                dishFlavorMapper.insertDishFlavor(dishFlavors);
            }

    }
}
