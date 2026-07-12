package com.codecoachai.resume.mapper.careerimport;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CareerImportDedupeGuardMapper {

    @Insert("""
            INSERT IGNORE INTO career_import_dedupe_guard (user_id, identity_hash)
            VALUES (#{userId}, #{identityHash})
            """)
    int insertIgnore(@Param("userId") Long userId, @Param("identityHash") String identityHash);

    @Select("""
            SELECT identity_hash
              FROM career_import_dedupe_guard
             WHERE user_id = #{userId}
               AND identity_hash = #{identityHash}
             FOR UPDATE
            """)
    String selectForUpdate(@Param("userId") Long userId, @Param("identityHash") String identityHash);
}
