package com.sky.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.sky.constant.MessageConstant;
import com.sky.constant.PasswordConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.EmployeeEditPasswordDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.BaseException;
import com.sky.exception.PasswordErrorException;
import com.sky.context.BaseContext;
import com.sky.mapper.EmployeeMapper;
import com.sky.result.PageResult;
import com.sky.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    /**
     * 员工登录
     *
     * @param employeeLoginDTO
     * @return
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        //1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        //2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (employee == null) {
            //账号不存在
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //密码比对

        password=DigestUtils.md5DigestAsHex(password.getBytes());
        if (!password.equals(employee.getPassword())) {
            //密码错误
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (employee.getStatus() == StatusConstant.DISABLE) {
            //账号被锁定
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //3、返回实体对象
        return employee;
    }

    @Override
    public void insert(Employee employee) {
        if(employee.getUsername()==null||!employee.getUsername().matches("^[a-z0-9]{3,20}$")){
            throw new BaseException("账号输入不符，请输入3-20个小写字母或数字");
        }
        if(employee.getPhone()==null||employee.getPhone().length()!=11){
            throw new BaseException("手机号码必须是11位");
        }
        if(employee.getIdNumber()==null||employee.getIdNumber().length()!=18){
            throw new  BaseException("身份证号码必须是18位");
        }
        employee.setPassword(DigestUtils.md5DigestAsHex(PasswordConstant.DEFAULT_PASSWORD.getBytes()));
        //employee.setCreateTime(LocalDateTime.now());
       // employee.setUpdateTime(LocalDateTime.now());

        // 设置创建人和修改人为当前登录用户
        //employee.setCreateUser(BaseContext.getCurrentId());
        //employee.setUpdateUser(BaseContext.getCurrentId());
        employee.setStatus(StatusConstant.ENABLE);
        employeeMapper.insert(employee);
    }

    @Override
    public PageResult page(EmployeePageQueryDTO employeePageQueryDTO) {
        //开启分页
        PageHelper.startPage(employeePageQueryDTO.getPage(),employeePageQueryDTO.getPageSize());
        //查询
        List<Employee> list = employeeMapper.page(employeePageQueryDTO.getName());
        //封装
        PageInfo<Employee> pageInfo = new PageInfo<>(list);
        //返回
        return  new PageResult(pageInfo.getTotal(),pageInfo.getList());
    }
    //更新员工
    @Override
    public void statusUpdate(Employee employee) {
        employeeMapper.update(employee);
    }

    @Override
    public Employee selectById(Long id) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw new BaseException("员工不存在");
        }
        employee.setPassword("****");
        return employee;
    }
    //编辑员工信息
    @Override
    public void update(Employee employee) {
        //employee.setUpdateTime(LocalDateTime.now());
       //employee.setUpdateUser(BaseContext.getCurrentId());
        employeeMapper.update(employee);
    }

    @Override
    public void editPassword(EmployeeEditPasswordDTO employeeEditPasswordDTO) {
        Long empId = BaseContext.getCurrentId();
        String oldPassword = employeeEditPasswordDTO.getOldPassword();
        String newPassword = employeeEditPasswordDTO.getNewPassword();

        // 根据id查询员工
        Employee employee = employeeMapper.selectById(empId);
        if (employee == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        // 验证旧密码是否正确
        oldPassword = DigestUtils.md5DigestAsHex(oldPassword.getBytes());
        if (!oldPassword.equals(employee.getPassword())) {
            throw new BaseException(MessageConstant.PASSWORD_ERROR);
        }

        // 检查新旧密码是否相同
        newPassword = DigestUtils.md5DigestAsHex(newPassword.getBytes());
        if (newPassword.equals(employee.getPassword())) {
            throw new BaseException("新密码不能与旧密码相同");
        }

        // 更新密码
        employee.setPassword(newPassword);
        employeeMapper.update(employee);
    }
}
