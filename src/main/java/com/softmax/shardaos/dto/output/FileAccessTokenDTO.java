package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class FileAccessTokenDTO {
    private String accessToken;
    private String secureUrl;
    private LocalDateTime expiresAt;
}