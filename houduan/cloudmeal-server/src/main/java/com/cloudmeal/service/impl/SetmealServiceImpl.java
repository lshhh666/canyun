package com.cloudmeal.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.cloudmeal.dto.SetmealDTO;
import com.cloudmeal.dto.SetmealPageQueryDTO;
import com.cloudmeal.entity.Setmeal;
import com.cloudmeal.exception.BaseException;
import com.cloudmeal.mapper.SetmealMapper;
import com.cloudmeal.mapper.SetmealdishMapper;
import com.cloudmeal.result.PageResult;
import com.cloudmeal.service.SetmealService;
import com.cloudmeal.vo.DishItemVO;
import com.cloudmeal.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SetmealServiceImpl implements SetmealService {
    @Autowired
    SetmealMapper setmealMapper;
    @Autowired
    SetmealdishMapper setmealdishMapper;
    @Override
    public Long insertSetmeal(SetmealDTO setmealDTO) {
        Setmeal setmeal =new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        setmeal.setStatus(0);
        setmealMapper.insertSetmeal(setmeal);
        return setmeal.getId();
    }

    @Override
    public void deleteSetmealByIds(List<Long> ids) {
        setmealMapper.deleteSetmealByIds(ids);
    }

    @Override
    public SetmealVO getSetmealById(Long id) {
        SetmealVO setmealVO = setmealMapper.getSetmealById(id);
        if(setmealVO == null){
            throw new BaseException("套餐不存在");
        }
        setmealVO.setSetmealDishes(setmealdishMapper.getSetmealdishById(id));
        return setmealVO;
    }

    @Override
    public PageResult SetmealList(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(),setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> setmealVOPage = setmealMapper.SetmealList(setmealPageQueryDTO);
        return  new  PageResult(setmealVOPage.getTotal(),setmealVOPage.getResult());
    }

    @Override
    public void updateSetmeal(SetmealDTO setmealDTO) {
            Setmeal setmeal =new Setmeal();
            BeanUtils.copyProperties(setmealDTO,setmeal);
            setmealMapper.update(setmeal);
    }

    @Override
    public void changestatus(Integer status,Long id) {
        Setmeal setmeal = new Setmeal();
        setmeal.setId(id);
        setmeal.setStatus(status);
        setmealMapper.update(setmeal);
    }

    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }

}
