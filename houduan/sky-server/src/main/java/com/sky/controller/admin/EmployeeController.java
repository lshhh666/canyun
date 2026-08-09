package com.sky.controller.admin;

import com.sky.constant.JwtClaimsConstant;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeEditPasswordDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.properties.JwtProperties;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.EmployeeService;
import com.sky.utils.JwtUtil;
import com.sky.vo.EmployeeLoginVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 员工管理
 */
@RestController
@RequestMapping("/admin/employee")
@Slf4j
@Api(tags = "员工相关接口")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 登录
     *
     * @param employeeLoginDTO
     * @return
     */
    @PostMapping("/login")
    @ApiOperation(value = "员工登入 ")
    public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO employeeLoginDTO) {
        log.info("员工登录：{}", employeeLoginDTO);

        Employee employee = employeeService.login(employeeLoginDTO);

        //登录成功后，生成jwt令
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims);

        EmployeeLoginVO employeeLoginVO = EmployeeLoginVO.builder()
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .token(token)
                .build();

        return Result.success(employeeLoginVO);
    }

    /**
     * 退出
     *
     * @return
     */
    @ApiOperation("员工退出")
    @PostMapping("/logout")
    public Result<String> logout() {
        return Result.success();
    }
    //新增员工
    @PostMapping
    @ApiOperation("新增员工")
    public Result<Object> insert(@RequestBody EmployeeDTO employeeDTO) {
        log.info("新增员工：{}", employeeDTO);

        // DTO 转 Entity
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);

        employeeService.insert(employee);
        return Result.success();
    }
    //分页查询员工
    @ApiOperation("员工分页查询")
    @GetMapping("/page")
    public Result<PageResult> page(EmployeePageQueryDTO  employeePageQueryDTO){
        log.info("查询的参数{}", employeePageQueryDTO);
        PageResult pageResult=employeeService.page(employeePageQueryDTO);
        return Result.success(pageResult);
    }
    //员工账号的状态更新
    @ApiOperation("启用,禁用员工账号")
    @PostMapping("/status/{status}")
    public Result statusUpdate(@PathVariable Integer status,EmployeeDTO employeeDTO) {
        log.info("id和status{}，{}",employeeDTO.getId(),status);
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);
        employee.setStatus(status);
        employeeService.statusUpdate(employee);
        return Result.success();
    }
    //根据id查询员工
    @ApiOperation("根据id查询员工")
    @GetMapping("/{id}")
    public Result<Employee> selectById(@PathVariable Long id){
         Employee employee=employeeService.selectById(id);
         return Result.success(employee);
    }
    //修改员工信息
    @ApiOperation("编辑员工信息")
    @PutMapping
    public Result update(@RequestBody EmployeeDTO employeeDTO){
            Employee employee = new Employee();
            BeanUtils.copyProperties(employeeDTO, employee);
            employeeService.update(employee);
            return Result.success();
    }

    //修改密码
    @ApiOperation("修改密码")
    @PutMapping("/editPassword")
    public Result editPassword(@RequestBody EmployeeEditPasswordDTO employeeEditPasswordDTO){
        log.info("修改密码：{}", employeeEditPasswordDTO);
        employeeService.editPassword(employeeEditPasswordDTO);
        return Result.success();
    }
}
