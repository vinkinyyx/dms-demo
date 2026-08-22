/*
 * 供应商实体
 */
package com.dms.masterdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "suppliers")
@TableName("suppliers")
public class Supplier {

    @Id
    @TableId(type = IdType.AUTO)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    private String code;

    private String name;

    @Column(name = "contact_person")
    private String contactPerson;

    @Column(name = "contact_phone")
    private String contactPhone;

    private String address;

    @Column(name = "bank_account")
    private String bankAccount;

    @Column(name = "tax_no")
    private String taxNo;

    private String remark;

    private String level;

    private String status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    public void ensureAttrs() {
        if (status == null) status = "active";
        if (createdAt == null) createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }
}