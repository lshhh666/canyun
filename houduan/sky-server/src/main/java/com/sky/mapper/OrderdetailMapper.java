package com.sky.mapper;

import com.sky.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderdetailMapper {

    void add(OrderDetail orderDetail);
    @Select("select * from order_detail where order_id=#{id}")
    List<OrderDetail> getByOrderId(Long id);
}
