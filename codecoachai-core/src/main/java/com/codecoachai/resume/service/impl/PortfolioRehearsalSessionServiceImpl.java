package com.codecoachai.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.constant.CommonConstants;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.context.LoginUserContext;
import com.codecoachai.resume.domain.dto.PortfolioRehearsalSessionSaveDTO;
import com.codecoachai.resume.domain.entity.PortfolioRehearsalSession;
import com.codecoachai.resume.domain.vo.PortfolioRehearsalSessionVO;
import com.codecoachai.resume.mapper.PortfolioRehearsalSessionMapper;
import com.codecoachai.resume.service.PortfolioRehearsalSessionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PortfolioRehearsalSessionServiceImpl implements PortfolioRehearsalSessionService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    /** Guardrail: bounded so a client cannot bloat the VARCHAR(2000) node-id column. */
    private static final int MAX_COMPLETED_NODES = 64;
    private static final int MAX_NODE_ID_LENGTH = 64;

    private final PortfolioRehearsalSessionMapper sessionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public PortfolioRehearsalSessionVO current() {
        PortfolioRehearsalSession session = currentSession();
        return session == null ? emptyView() : toView(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortfolioRehearsalSessionVO save(PortfolioRehearsalSessionSaveDTO dto) {
        Long userId = currentUserId();
        String completedJson = writeCompletedNodeIds(dto.getCompletedNodeIds());

        PortfolioRehearsalSession session = currentSession();
        if (session == null) {
            session = new PortfolioRehearsalSession();
            session.setUserId(userId);
            applyMutations(session, dto, completedJson);
            try {
                sessionMapper.insert(session);
                return toView(session);
            } catch (DuplicateKeyException raced) {
                // Concurrent first-save from the same user; fall through to update the winner.
                session = currentSession();
                if (session == null) {
                    throw new BusinessException(ErrorCode.RESOURCE_RELATION_CONFLICT);
                }
            }
        }
        applyMutations(session, dto, completedJson);
        sessionMapper.updateById(session);
        return toView(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortfolioRehearsalSessionVO reset() {
        PortfolioRehearsalSession session = currentSession();
        if (session == null) {
            return emptyView();
        }
        session.setActiveRouteKey(null);
        session.setActiveNodeIndex(0);
        session.setElapsedSeconds(0);
        session.setCompletedNodeIds("[]");
        sessionMapper.updateById(session);
        return toView(session);
    }

    private void applyMutations(PortfolioRehearsalSession session,
                                PortfolioRehearsalSessionSaveDTO dto, String completedJson) {
        session.setActiveRouteKey(dto.getActiveRouteKey());
        session.setActiveNodeIndex(dto.getActiveNodeIndex());
        session.setElapsedSeconds(dto.getElapsedSeconds());
        session.setCompletedNodeIds(completedJson);
    }

    private PortfolioRehearsalSession currentSession() {
        Long userId = currentUserId();
        return sessionMapper.selectOne(new LambdaQueryWrapper<PortfolioRehearsalSession>()
                .eq(PortfolioRehearsalSession::getUserId, userId)
                .eq(PortfolioRehearsalSession::getDeleted, CommonConstants.NO)
                .last("limit 1"));
    }

    private String writeCompletedNodeIds(List<String> nodeIds) {
        Set<String> normalized = new LinkedHashSet<>();
        if (nodeIds != null) {
            for (String nodeId : nodeIds) {
                if (!StringUtils.hasText(nodeId)) {
                    continue;
                }
                String trimmed = nodeId.trim();
                if (trimmed.length() > MAX_NODE_ID_LENGTH) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "排练节点标识过长");
                }
                normalized.add(trimmed);
                if (normalized.size() > MAX_COMPLETED_NODES) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "已完成排练节点数量超出上限");
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(new ArrayList<>(normalized));
        } catch (Exception ex) {
            throw new IllegalStateException("排练进度序列化失败", ex);
        }
    }

    private List<String> readCompletedNodeIds(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return new ArrayList<>();
        }
        try {
            List<String> parsed = objectMapper.readValue(rawJson, STRING_LIST_TYPE);
            return parsed == null ? new ArrayList<>() : parsed;
        } catch (Exception ex) {
            // Persisted state should always be valid JSON we wrote; degrade to empty rather than fail a read.
            return new ArrayList<>();
        }
    }

    private PortfolioRehearsalSessionVO toView(PortfolioRehearsalSession session) {
        PortfolioRehearsalSessionVO vo = new PortfolioRehearsalSessionVO();
        vo.setActiveRouteKey(session.getActiveRouteKey());
        vo.setActiveNodeIndex(session.getActiveNodeIndex());
        vo.setElapsedSeconds(session.getElapsedSeconds());
        vo.setCompletedNodeIds(readCompletedNodeIds(session.getCompletedNodeIds()));
        vo.setUpdatedAt(session.getUpdatedAt());
        return vo;
    }

    private PortfolioRehearsalSessionVO emptyView() {
        PortfolioRehearsalSessionVO vo = new PortfolioRehearsalSessionVO();
        vo.setActiveRouteKey(null);
        vo.setActiveNodeIndex(0);
        vo.setElapsedSeconds(0);
        vo.setCompletedNodeIds(new ArrayList<>());
        return vo;
    }

    private Long currentUserId() {
        Long userId = LoginUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
