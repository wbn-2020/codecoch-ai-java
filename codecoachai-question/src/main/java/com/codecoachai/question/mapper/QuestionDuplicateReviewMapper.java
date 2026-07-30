package com.codecoachai.question.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codecoachai.question.domain.entity.QuestionDuplicateReview;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface QuestionDuplicateReviewMapper extends BaseMapper<QuestionDuplicateReview> {

    @Select("""
            <script>
            SELECT source_question_id AS sourceQuestionId,
                   target_question_id AS targetQuestionId
              FROM question_duplicate_review
             WHERE deleted = 0
               AND review_status IN ('PENDING', 'CONFIRMED', 'IGNORED')
               AND (
                    (source_question_id = #{questionId}
                     AND target_question_id IN
                     <foreach collection="candidateIds" item="candidateId" open="(" separator="," close=")">
                         #{candidateId}
                     </foreach>)
                    OR
                    (target_question_id = #{questionId}
                     AND source_question_id IN
                     <foreach collection="candidateIds" item="candidateId" open="(" separator="," close=")">
                         #{candidateId}
                     </foreach>)
               )
            </script>
            """)
    List<QuestionDuplicateReview> selectExistingPairs(@Param("questionId") Long questionId,
                                                       @Param("candidateIds") List<Long> candidateIds);
}
