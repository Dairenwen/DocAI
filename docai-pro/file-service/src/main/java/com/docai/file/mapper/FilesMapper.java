package com.docai.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docai.file.entity.FilesEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文件访问数据库的 mapper
 */
@Mapper
public interface FilesMapper extends BaseMapper<FilesEntity> {

    @Select("select *from files where user_id = #{userId} and id = #{fileId}")
    FilesEntity selectByUserIdAndFileId(@Param("userId") Long userId, @Param("fileId") Long fileId);
}
