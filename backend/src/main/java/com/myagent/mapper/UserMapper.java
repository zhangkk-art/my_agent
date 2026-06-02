package com.myagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myagent.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM users WHERE username = #{username}")
    User findByUsername(String username);
}
