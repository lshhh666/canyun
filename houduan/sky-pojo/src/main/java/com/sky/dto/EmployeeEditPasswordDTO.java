package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class EmployeeEditPasswordDTO implements Serializable {

    private String oldPassword;

    private String newPassword;

}