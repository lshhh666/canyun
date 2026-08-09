package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.BaseException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.result.PageResult;
import com.sky.service.CategoryService;
import com.sky.service.DishFlavorService;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class DIshServiceImpl implements DishService {
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    @Autowired
    private DishFlavorService dishFlavorService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Override
    public Long insertDish(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO,dish);
        dishMapper.insertDish(dish);
        return dish.getId();
    }

    @Override
    public PageResult pageDish(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(),dishPageQueryDTO.getPageSize());
        Page<DishVO> dishPage= dishMapper.pageDish(dishPageQueryDTO);
        return new PageResult(dishPage.getTotal(), dishPage.getResult());
    }

    @Override
    public void deleteDishByIds(List<Long> dishIds) {
        dishFlavorMapper.deleteByDishIds(dishIds);
        dishMapper.deleteDishByIds(dishIds);
    }

    @Override
    public DishVO getDishById(Long id) {
      DishVO dishVO=dishMapper.getDishById(id);
        if (dishVO == null) {
            throw new BaseException("菜品不存在");
        }
        List<DishFlavor>dishFlavors= dishFlavorMapper.getByDishId(id);
      dishVO.setFlavors(dishFlavors);
      return dishVO;
    }

    @Override
    public void updateDish(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO,dish);
        dishMapper.update(dish);
        dishFlavorMapper.deleteByDishIds(Collections.singletonList(dishDTO.getId()));
        dishFlavorService.insertDishFlavor(dishDTO.getFlavors(),dishDTO.getId());
    }

    @Override
    public List<Dish> getDishByCategoryId(Long categoryId) {
       Dish dish=new Dish();
       dish.setCategoryId(categoryId);
       return dishMapper.list(dish);
    }

    @Override
    public void statusUpdateDish(Dish dish) {
        dishMapper.update(dish);
    }

    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    public List<DishVO> listWithFlavor(Dish dish) {
        //用reids 缓存
        String key="dish_category_"+dish.getCategoryId();
        List<DishVO> dishVOList =(List<DishVO>)redisTemplate.opsForValue().get(key);
        if(dishVOList != null && dishVOList.size()>0){
            return dishVOList;
        }

        List<Dish> dishList = dishMapper.list(dish);

        dishVOList = new ArrayList<>();

        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d,dishVO);

            //根据菜品id查询对应的口味
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());

            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }
    redisTemplate.opsForValue().set(key,dishVOList,30, TimeUnit.MINUTES);

        return dishVOList;
    }

}
