package com.docai.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docai.file.entity.FieldMappingEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


/**
 * 字段硬是关系表mapper
 */
@Mapper
public interface FieldMappingMapper extends BaseMapper<FieldMappingEntity> {


    @Insert("<script>" +
            "INSERT INTO field_mappings (file_id, table_name, db_field_name, original_header, field_order) " +
            "VALUES " +
            "<foreach collection='mappings' item='mapping' separator=','> " +
            "(#{mapping.fileId}, #{mapping.tableName}, #{mapping.dbFieldName}, #{mapping.originalHeader}, #{mapping.fieldOrder}) " +
            "</foreach>" +
            "</script>"
    )
    int batchInsert(@Param("mappings")List<FieldMappingEntity> mappings);

}
