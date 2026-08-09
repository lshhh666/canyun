package com.sky.mapper;

import com.sky.annotation.AutoFill;
import com.sky.entity.Employee;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EmployeeMapper {

    /**
     * 根据用户名查询员工
     * @param username
     * @return
     */
    @Select("select * from employee where username = #{username}")
    Employee getByUsername(String username);
    //新增员工
    @Insert("insert into employee(username, name, password, phone, sex, id_number, status, create_time, update_time, create_user, update_user) " +
            "values (#{username}, #{name}, #{password}, #{phone}, #{sex}, #{idNumber}, #{status}, #{createTime}, #{updateTime}, #{createUser}, #{updateUser}) ")
    @AutoFill(OperationType.INSERT)
    void insert(Employee employee);
    //分页查询
    List<Employee> page(String name);
    //账号的启用和禁用
    @AutoFill(OperationType.UPDATE)
    void update(Employee employee);
    @Select("select id, username, name, password, phone, sex, id_number, status, create_time, update_time, create_user, update_user from employee where id=#{id}")
    Employee selectById(Long id);
}
