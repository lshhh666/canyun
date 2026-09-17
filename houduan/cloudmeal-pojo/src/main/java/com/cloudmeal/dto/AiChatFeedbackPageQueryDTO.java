package com.cloudmeal.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDate;

/** 管理端未解决评价分页查询条件。 */
@Data
public class AiChatFeedbackPageQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页码，从1开始。 */
    private Integer page;

    /** 每页记录数。 */
    private Integer pageSize;

    /** 最后评价日期下限，包含当天。 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate begin;

    /** 最后评价日期上限，包含当天。 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate end;
}
