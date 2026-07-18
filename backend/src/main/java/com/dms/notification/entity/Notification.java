/*
 * 通知实体：映射 notifications 表。channel: INAPP / WECHAT_BOT / FEISHU_BOT。
 */
package com.dms.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 16)
    private String channel;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String body;

    @Column(name = "ref_type", length = 32)
    private String refType;

    @Column(name = "ref_id", length = 64)
    private String refId;

    @Column(name = "is_read")
    private Boolean isRead;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
