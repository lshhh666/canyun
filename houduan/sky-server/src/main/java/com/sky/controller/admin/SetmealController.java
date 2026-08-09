package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.SetmealDish;
import com.sky.vo.DishVO;
import com.sky.exception.BaseException;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.service.SetmealService;
import com.sky.service.SetmealdishService;
import com.sky.vo.SetmealVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/admin/setmeal")
@Api(tags = "套餐相关接口")
public class SetmealController {
    @Autowired
    SetmealService setmealService;
    @Autowired
    SetmealdishService setmealdishService;
    @Autowired
    DishService dishService;
    //新增套餐
    @Transactional(rollbackFor = Exception.class)
    @ApiOperation("新增套餐")
    @PostMapping
    @CacheEvict(cacheNames = "setmealCache",key = "#setmealDTO.categoryId")
    public Result<Object> insertSetmeal(@RequestBody SetmealDTO setmealDTO) {
                log.info("新增的套餐:{}", setmealDTO);
                Long setmealid=setmealService.insertSetmeal(setmealDTO);
                setmealdishService.insertSetmeal(setmealDTO,setmealid);
                return Result.success();
    }
    //批量删除套餐
    @Transactional(rollbackFor = Exception.class)
    @ApiOperation("批量删除套餐")
    @DeleteMapping
    @CacheEvict(cacheNames = "setmealCache",allEntries = true)
    public Result deleteSetmealByIds(@RequestParam List<Long> ids) {
        log.info("删除的id:{}",ids);
        //检查套餐是否起售中
        for (Long id : ids) {
            SetmealVO setmealVO = setmealService.getSetmealById(id);
            if (StatusConstant.ENABLE.equals(setmealVO.getStatus())) {
                throw new BaseException(MessageConstant.SETMEAL_ON_SALE);
            }
        }
        setmealdishService.deleteSetmealdishByIds(ids);
        setmealService.deleteSetmealByIds(ids);
        return Result.success();
    }
    //根据id查询套餐
    @ApiOperation("根据id查询套餐")
    @GetMapping("{id}")
    public Result<SetmealVO> getSetmealById(@PathVariable Long id) {
            log.info("查询的id{}",id);
            SetmealVO setmealVO=setmealService.getSetmealById(id);
            return Result.success(setmealVO);
    }
    //分页查询
    @ApiOperation("分页查询接口")
    @GetMapping("/page")
    public  Result<PageResult> setmealList(SetmealPageQueryDTO  setmealPageQueryDTO) {
            log.info("分页查询{}",setmealPageQueryDTO);
            PageResult pageResult=setmealService.SetmealList(setmealPageQueryDTO);
            return Result.success(pageResult);
    }
    @Transactional(rollbackFor = Exception.class)
    @ApiOperation("修改套餐接口")
    @PutMapping
    @CacheEvict(cacheNames = "setmealCache",allEntries = true)
    public Result updateSetmeal(@RequestBody SetmealDTO setmealDTO){
            log.info("修改的对象{}",setmealDTO);
            setmealService.updateSetmeal(setmealDTO);
            setmealdishService.deleteSetmealdishByIds(Collections.singletonList(setmealDTO.getId()));
            setmealdishService.insertSetmeal(setmealDTO,setmealDTO.getId());
            return Result.success();
    }
    //套餐的起售停售
    @ApiOperation("套餐的起售和停售")
    @PostMapping("/status/{status}")
    @CacheEvict(cacheNames = "setmealCache",allEntries = true)
    public Result changestatus(@PathVariable Integer status,@RequestParam Long id){
        log.info("套餐状态{},{}",status,id);
        //启售时检查套餐内是否有未启售的菜品
        if (StatusConstant.ENABLE.equals(status)) {
            List<SetmealDish> setmealDishes = setmealdishService.getSetmealdishById(id);
            if (setmealDishes != null && !setmealDishes.isEmpty()) {
                for (SetmealDish setmealDish : setmealDishes) {
                    DishVO dishVO = dishService.getDishById(setmealDish.getDishId());
                    if (dishVO != null && !StatusConstant.ENABLE.equals(dishVO.getStatus())) {
                        throw new BaseException(MessageConstant.SETMEAL_ENABLE_FAILED);
                    }
                }
            }
        }
        setmealService.changestatus(status,id);
        return Result.success();
    }
}
