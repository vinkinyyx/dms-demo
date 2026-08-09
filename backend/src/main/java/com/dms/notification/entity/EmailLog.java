/*
 * 邮件发送日志实体。
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
@Table(name = "email_logs")
public class EmailLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "from_address", length = 200)
    private String fromAddress;

    @Column(name = "to_address", length = 256, nullable = false)
    private String toAddress;

    @Column(length = 256)
    private String subject;

    @Column(columnDefinition = "text")
    private String text;

    @Column(length = 16, nullable = false)
    private String status;

    @Column(name = "biz_type", length = 64)
    private String bizType;

    @Column(name = "biz_id", length = 64)
    private String bizId;

    @Column(name = "recipient_user_id")
    private Long recipientUserId;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}