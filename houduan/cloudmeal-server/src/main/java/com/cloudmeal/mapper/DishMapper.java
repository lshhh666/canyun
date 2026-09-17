package com.cloudmeal.mapper;

import com.github.pagehelper.Page;
import com.cloudmeal.annotation.AutoFill;
import com.cloudmeal.dto.DishPageQueryDTO;
import com.cloudmeal.entity.Dish;
import com.cloudmeal.enumeration.OperationType;
import com.cloudmeal.vo.DishVO;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface DishMapper {

    /**
     * 根据分类id查询菜品数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from dish where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);
    @Insert("insert into dish(name, category_id, price, image, description, status, create_time, update_time, create_user, update_user) " +
            "values(#{name}, #{categoryId}, #{price}, #{image}, #{description}, #{status}, #{createTime}, #{updateTime}, #{createUser}, #{updateUser})")
    @AutoFill(OperationType.INSERT)
    @Options(useGeneratedKeys = true,keyProperty = "id")
    void insertDish(Dish dish);
    //菜品分类查询
    Page<DishVO> pageDish(DishPageQueryDTO dishPageQueryDTO);

    void deleteDishByIds(@Param("dishIds")List<Long> dishIds);

    DishVO getDishById(Long id);

    @Select("select id, category_id from dish where id = #{id} for update")
    Dish getByIdForUpdate(Long id);
    @AutoFill(OperationType.UPDATE)
    void update(Dish dish);

    List<Dish> list(Dish dish);

    Integer countByMap(Map map);
}
