package com.codecoachai.user.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class UserProfileVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String email;
    private Integer status;
    private List<String> roles;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
