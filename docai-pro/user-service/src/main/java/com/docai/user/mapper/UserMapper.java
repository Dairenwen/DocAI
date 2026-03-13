package com.docai.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docai.user.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户表
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {


    /**
     * 根据邮箱或者用户名二选一去查询用户
     * @param key 邮箱或者用户名
     * @return 用户
     */
    @Select("select id, username, email, password_hash as passwordHash from users where username=#{key} or email=#{key} limit 1")
    UserEntity findByLoginKey(@Param("key") String key);

    /**
     * 判断用户名是否已经存在
     * @param username 待判断的用户名
     * @return 0表示不存在 >0表示占用
     */
    @Select("select count(1) from users where username = #{username}")
    int existByUserName(@Param("username") String username);

}
