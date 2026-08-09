package com.sky.controller.user;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.result.Result;
import com.sky.service.ShoppingCartService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/user/shoppingCart")
@Api(tags = "C端-购物车接口")
public class ShoppingCartController {
    @Autowired
    private ShoppingCartService shoppingCartService;
    //添加购物车
    @PostMapping("/add")
    @ApiOperation("添加购物车接口")
    public Result addShoppingCart(@RequestBody ShoppingCartDTO  shoppingCartDTO) {
        log.info("addShoppingCart{}",shoppingCartDTO);
        shoppingCartService.addShoppingCart(shoppingCartDTO);
        return Result.success();
    }
    //查看购物车
    @GetMapping("/list")
    @ApiOperation("查看购物车接口")
    public Result<List<ShoppingCart>> listShoppingCart() {
        log.info("listShoppingCart");
      List<ShoppingCart> shoppingCartList=shoppingCartService.listShoppingCart();
      return Result.success(shoppingCartList);
    }
    @ApiOperation("删除购物车中一个商品")
    @PostMapping("/sub")
    public Result subShoppingCart(@RequestBody ShoppingCartDTO shoppingCartDTO) {
        log.info("删除的商品{}",shoppingCartDTO);
        shoppingCartService.subShoppingCart(shoppingCartDTO);
        return Result.success();
    }
    @ApiOperation("清空购物车")
    @DeleteMapping("/clean")
    public Result deleteShoppingCart() {
        shoppingCartService.deleteShoppingCart();
        return Result.success();
    }
}
