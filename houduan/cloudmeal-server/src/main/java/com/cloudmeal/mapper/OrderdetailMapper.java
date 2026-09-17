package com.cloudmeal.mapper;

import com.cloudmeal.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderdetailMapper {

    void add(OrderDetail orderDetail);
    @Select("select * from order_detail where order_id=#{id}")
    List<OrderDetail> getByOrderId(Long id);

    /** AI客服只读取菜品名称、规格、数量和下单时单价。 */
    @Select("select name, dish_flavor, number, amount from order_detail where order_id=#{orderId} order by id")
    List<OrderDetail> listAiDetailByOrderId(Long orderId);
}
