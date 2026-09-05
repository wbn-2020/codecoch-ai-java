package com.codecoachai.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.file.domain.entity.FileInfo;
import com.codecoachai.file.domain.vo.FileResumeAnalysisStatusVO;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {

    @Insert("""
            INSERT IGNORE INTO resume_upload_dedupe_guard (
              user_id, content_sha256, created_at, updated_at
            ) VALUES (
              #{userId}, #{contentSha256}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            )
            """)
    int acquireResumeUploadGuard(
            @Param("userId") Long userId,
            @Param("contentSha256") String contentSha256);

    @Update("""
            UPDATE resume_upload_dedupe_guard
            SET file_id = #{fileId},
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = #{userId}
              AND content_sha256 = #{contentSha256}
            """)
    int bindResumeUploadGuard(
            @Param("userId") Long userId,
            @Param("contentSha256") String contentSha256,
            @Param("fileId") Long fileId);

    @Delete("""
            DELETE guard_row
            FROM resume_upload_dedupe_guard guard_row
            WHERE guard_row.user_id = #{userId}
              AND guard_row.content_sha256 = #{contentSha256}
              AND NOT EXISTS (
                SELECT 1
                FROM file_info file_row
                WHERE file_row.user_id = guard_row.user_id
                  AND file_row.biz_type = 'RESUME'
                  AND file_row.content_sha256 = guard_row.content_sha256
                  AND file_row.status = 'AVAILABLE'
                  AND file_row.deleted = 0
              )
            """)
    int deleteStaleResumeUploadGuard(
            @Param("userId") Long userId,
            @Param("contentSha256") String contentSha256);

    @Delete("""
            DELETE FROM resume_upload_dedupe_guard
            WHERE user_id = #{userId}
              AND content_sha256 = #{contentSha256}
              AND file_id IS NULL
            """)
    int releaseUnboundResumeUploadGuard(
            @Param("userId") Long userId,
            @Param("contentSha256") String contentSha256);

    @Select("""
            SELECT *
            FROM file_info
            WHERE user_id = #{userId}
              AND biz_type = #{bizType}
              AND content_sha256 = #{contentSha256}
              AND status = 'AVAILABLE'
              AND deleted = 0
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
    FileInfo selectLatestAvailableByContentSha256(
            @Param("userId") Long userId,
            @Param("bizType") String bizType,
            @Param("contentSha256") String contentSha256);

    @Select("""
            <script>
            SELECT
              latest.file_id AS fileId,
              latest.resume_id AS resumeId,
              latest.id AS resumeAnalysisRecordId,
              latest.parse_status AS parseStatus,
              latest.error_message AS parseErrorMessage,
              latest.created_at AS createdAt,
              latest.updated_at AS updatedAt
            FROM resume_analysis_record latest
            INNER JOIN (
              SELECT file_id, MAX(CONCAT(DATE_FORMAT(created_at, '%Y%m%d%H%i%s'), LPAD(id, 20, '0'))) AS sort_key
              FROM resume_analysis_record
              WHERE deleted = 0
                AND file_id IN
                <foreach collection="fileIds" item="fileId" open="(" separator="," close=")">
                  #{fileId}
                </foreach>
              GROUP BY file_id
            ) picked ON picked.file_id = latest.file_id
              AND picked.sort_key = CONCAT(DATE_FORMAT(latest.created_at, '%Y%m%d%H%i%s'), LPAD(latest.id, 20, '0'))
            WHERE latest.deleted = 0
            </script>
            """)
    List<FileResumeAnalysisStatusVO> selectLatestResumeAnalysisByFileIds(@Param("fileIds") List<Long> fileIds);

    @Select("""
            SELECT latest.file_id
            FROM resume_analysis_record latest
            INNER JOIN (
              SELECT file_id, MAX(CONCAT(DATE_FORMAT(created_at, '%Y%m%d%H%i%s'), LPAD(id, 20, '0'))) AS sort_key
              FROM resume_analysis_record
              WHERE deleted = 0
              GROUP BY file_id
            ) picked ON picked.file_id = latest.file_id
              AND picked.sort_key = CONCAT(DATE_FORMAT(latest.created_at, '%Y%m%d%H%i%s'), LPAD(latest.id, 20, '0'))
            WHERE latest.deleted = 0
              AND latest.parse_status = #{parseStatus}
            """)
    List<Long> selectLatestResumeFileIdsByParseStatus(@Param("parseStatus") String parseStatus);

    @Select("""
            SELECT
              file_id AS fileId,
              resume_id AS resumeId,
              id AS resumeAnalysisRecordId,
              parse_status AS parseStatus,
              error_message AS parseErrorMessage,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM resume_analysis_record
            WHERE deleted = 0
              AND file_id = #{fileId}
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
    FileResumeAnalysisStatusVO selectLatestResumeAnalysisByFileId(@Param("fileId") Long fileId);
}
