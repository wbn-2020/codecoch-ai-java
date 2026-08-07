package com.codecoachai.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.file.domain.entity.FileInfo;
import com.codecoachai.file.domain.vo.FileResumeAnalysisStatusVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {

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
