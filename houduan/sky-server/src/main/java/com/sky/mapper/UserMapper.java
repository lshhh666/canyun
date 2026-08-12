package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

@Mapper
public interface UserMapper {
    @Select("select * from user where openid=#{openid}")
    User getByOpenid(String openid);

    @Select("select * from user where id=#{id}")
    User getById(Long id);

    @Update("update user set name=#{name}, avatar=#{avatar} where id=#{id}")
    void updateProfile(User user);

    @Insert("insert into  user(openid,create_time) values(#{openid},#{createTime})")
    @Options(useGeneratedKeys = true,keyProperty = "id")
    void insert(User user);

    Integer countByMap(Map map);
}
