/*
 * MinIO 对象存储封装：上传字节、下载、确保 bucket 存在。
 */
package com.dms.config;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @PostConstruct
    public void init() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build());
                log.info("MinIO bucket 已创建: {}", minioConfig.getBucket());
            }
        } catch (Exception e) {
            log.warn("MinIO 初始化检查失败（对象存储相关功能可能不可用）: {}", e.getMessage());
        }
    }

    public void put(String objectKey, byte[] content, String contentType) {
        try (InputStream in = new ByteArrayInputStream(content)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectKey)
                    .stream(in, content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("上传文件到 MinIO 失败: " + e.getMessage(), e);
        }
    }

    public InputStream get(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("从 MinIO 下载文件失败: " + e.getMessage(), e);
        }
    }

    public String bucket() {
        return minioConfig.getBucket();
    }
}