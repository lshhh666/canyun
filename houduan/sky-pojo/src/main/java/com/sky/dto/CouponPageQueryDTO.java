package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理端优惠券分页查询条件。
 */
@Data
public class CouponPageQueryDTO implements Serializable {

    private int page;
    private int pageSize;
    private String name;
    /** 状态：0草稿，1发放中，2已停用。 */
    private Integer status;
}
