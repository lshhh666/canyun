package com.sky.service.impl;

import com.sky.dto.DishDTO;
import com.sky.entity.DishFlavor;
import com.sky.mapper.DishFlavorMapper;
import com.sky.service.DishFlavorService;
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
