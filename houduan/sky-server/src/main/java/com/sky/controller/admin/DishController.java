package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.SetmealDish;
import com.sky.exception.BaseException;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishFlavorService;
import com.sky.service.DishService;
import com.sky.service.SetmealdishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/admin/dish")
@Api(tags = "菜品相关接口")
public class DishController {
    @Autowired
    private DishService dishService;
    @Autowired
    private DishFlavorService dishFlavorService;
    @Autowired
    private SetmealdishService setmealdishService;
    @Autowired
    private RedisTemplate  redisTemplate;
    @Transactional(rollbackFor = Exception.class)
    @ApiOperation("新增菜品接口")
    @PostMapping
    public Result<String> insertDish(@RequestBody DishDTO dishDTO) {
            log.info("新增菜品{}", dishDTO);
            Long dishId=dishService.insertDish(dishDTO);
            dishFlavorService.insertDishFlavor(dishDTO.getFlavors(),dishId);
            redisTemplate.delete("dish_category_"+dishDTO.getCategoryId());
            return Result.success();
    }
    //菜品分页查询
    @ApiOperation("菜品分页查询接口")
    @GetMapping("/page")
    public Result<PageResult> pageDish(DishPageQueryDTO dishPageQueryDTO) {
            log.info("菜品分页查询{}", dishPageQueryDTO);
            PageResult pageResult=dishService.pageDish(dishPageQueryDTO);
            return Result.success(pageResult);
    }
    //删除菜品
    @Transactional(rollbackFor = Exception.class)
    @ApiOperation("删除菜品接口")
    @DeleteMapping
    public Result<String> deleteDishByIds(@RequestParam("ids")List<Long> dishIds) {
        log.info("批量删除菜品：{}", dishIds);
        //检查菜品是否起售中
        for (Long dishId : dishIds) {
            DishVO dishVO = dishService.getDishById(dishId);
            if (StatusConstant.ENABLE.equals(dishVO.getStatus())) {
                throw new BaseException(MessageConstant.DISH_ON_SALE);
            }
            //检查菜品是否关联了套餐
            List<SetmealDish> setmealDishes = setmealdishService.getSetmealdishByDishId(dishId);
            if (setmealDishes != null && setmealDishes.size() > 0) {
                throw new BaseException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
            }
            redisTemplate.delete("dish_category_"+dishVO.getCategoryId());
        }
        dishService.deleteDishByIds(dishIds);
        return Result.success();
    }
    //根据id查询菜品
    @ApiOperation("根据id查询菜品")
    @GetMapping("{id}")
    public Result<DishVO> getDishById(@PathVariable Long id) {
        log.info("查询的菜品id:{}",id);
        DishVO dishVO=dishService.getDishById(id);
        return Result.success(dishVO);
    }
    //修改菜品
    @Transactional(rollbackFor = Exception.class)
    @ApiOperation("修改菜品接口")
    @PutMapping
    public Result<String> updateDish(@RequestBody DishDTO dishDTO) {
        log.info("修改菜品:{}",dishDTO);
        //检查菜品是否起售中
        DishVO dishVO = dishService.getDishById(dishDTO.getId());
        if (StatusConstant.ENABLE.equals(dishVO.getStatus())) {
            throw new BaseException(MessageConstant.DISH_ON_SALE);
        }
        redisTemplate.delete("dish_category_"+dishVO.getCategoryId());
        dishService.updateDish(dishDTO);
        redisTemplate.delete("dish_category_"+dishDTO.getCategoryId());
        return Result.success();
    }
    //根据分类id查询菜品
    @ApiOperation("根据分类id查询菜品接口")
    @GetMapping("/list")
    public Result<List<Dish>> getDishByCategoryId(Long categoryId) {
        log.info("根据分类id查询菜品：{}", categoryId);
        List<Dish> dishList=dishService.getDishByCategoryId(categoryId);
        return Result.success(dishList);
    }
    //菜品的起售或者停售
    @ApiOperation("菜品的起售和停售接口")
    @PostMapping("/status/{status}")
    public Result statusUpdateDish(@PathVariable Integer status,DishDTO dishDTO) {
        log.info("id和status:{},{}",dishDTO.getId(),status);
        Dish dish=new Dish();
        BeanUtils.copyProperties(dishDTO,dish);
        dish.setStatus(status);
        dishService.statusUpdateDish(dish);
        redisTemplate.delete("dish_category_"+dishDTO.getCategoryId());
        return Result.success();
    }
}
