package com.cloudmeal.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 管理员为一条“没解决”评价发起复测时提交的业务字段。 */
@Data
public class AiFeedbackRetestSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本次复测问题快照，默认使用评价对应的最后一个用户问题。 */
    private String question;

    /** 复测必须使用的稳定知识标识，可关联多条知识。 */
    private List<String> knowledgeKeys;
}
