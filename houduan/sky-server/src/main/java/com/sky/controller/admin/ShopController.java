package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.ShopService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController("adminShopController")
@RequestMapping("/admin/shop")
@Api(tags = "店铺相关接口")
public class ShopController {
    @Autowired
    private ShopService shopService;

    @ApiOperation("设置营业状态接口")
    @PutMapping("/{status}")
    public Result setShopStatus(@PathVariable Integer status){
        log.info("店铺状态设置{}",status);
        shopService.setShopStatus(status);
        return Result.success();
    }
    @ApiOperation("查询营业状态接口")
    @GetMapping("/status")
    public Result<Integer> getShopStatus(){
      Integer status=shopService.getShopStatus();
      log.info("店铺状态{}",status ==1 ? "营业中"  :  "打样”");
      return Result.success(status);
    }
}
