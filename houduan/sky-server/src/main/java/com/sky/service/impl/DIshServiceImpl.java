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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    @Transactional(rollbackFor = Exception.class)
    public void statusUpdateDish(Dish dish) {
        if (dish == null || dish.getId() == null || dish.getId() <= 0
                || (!Integer.valueOf(0).equals(dish.getStatus())
                && !Integer.valueOf(1).equals(dish.getStatus()))) {
            throw new BaseException("菜品ID或状态无效");
        }
        Dish current = dishMapper.getByIdForUpdate(dish.getId());
        if (current == null || current.getCategoryId() == null) {
            throw new BaseException("菜品不存在或分类无效");
        }
        Dish update = new Dish();
        update.setId(current.getId());
        update.setStatus(dish.getStatus());
        dishMapper.update(update);
        String cacheKey = "dish_category_" + current.getCategoryId();
        // 事务提交后才删除，避免修改尚未提交时读请求回填旧状态。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redisTemplate.delete(cacheKey);
            }
        });
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
