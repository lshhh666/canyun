package com.cloudmeal.dto;

import com.cloudmeal.enums.AiFeedbackRetestReviewResult;
import lombok.Data;

import java.io.Serializable;

/** 管理员对一次成功复测的人工确认。 */
@Data
public class AiFeedbackRetestReviewDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private AiFeedbackRetestReviewResult reviewResult;
}
