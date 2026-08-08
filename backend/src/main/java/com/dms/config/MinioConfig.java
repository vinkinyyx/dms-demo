/*
 * MinIO 客户端配置：从 dms.minio.* 读取并构建单例 MinioClient。
 */
package com.dms.config;

import io.minio.MinioClient;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class MinioConfig {

    @Value("${dms.minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${dms.minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${dms.minio.secret-key:minioadmin}")
    private String secretKey;

    @Value("${dms.minio.bucket:dms}")
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}