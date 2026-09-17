package com.cloudmeal.service;

import com.cloudmeal.dto.EmployeeEditPasswordDTO;
import com.cloudmeal.dto.EmployeeLoginDTO;
import com.cloudmeal.dto.EmployeePageQueryDTO;
import com.cloudmeal.entity.Employee;
import com.cloudmeal.result.PageResult;

public interface EmployeeService {

    /**
     * 员工登录
     *
     */
    Employee login(EmployeeLoginDTO employeeLoginDTO);
    //新增员工
    void insert(Employee employee);
    //员工分页查询
    PageResult page(EmployeePageQueryDTO employeePageQueryDTO);
    //员工账号状态更新
    void statusUpdate(Employee employee);
    //根据id查询员工
    Employee selectById(Long id);
    //修改员工信息
    void update(Employee employee);
    //修改密码
    void editPassword(EmployeeEditPasswordDTO employeeEditPasswordDTO);
}
