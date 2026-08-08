from pathlib import Path
base=Path('backend/src/main/java/com/dms/platform/config')
(base/'entity/TenantFilterConfig.java').write_text('''package com.dms.platform.config.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tenant_filter_configs")
public class TenantFilterConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "page_key", nullable = false, length = 100)
    private String pageKey;
    @Column(name = "filter_key", nullable = false, length = 100)
    private String filterKey;
    @Column(name = "label", nullable = false, length = 100)
    private String label;
    @Column(name = "component_type", nullable = false, length = 32)
    private String componentType;
    @Column(name = "dict_type", length = 100)
    private String dictType;
    @Column(name = "default_value")
    private String defaultValue;
    @Column(name = "multiple", nullable = false)
    private Boolean multiple;
    @Column(name = "visible", nullable = false)
    private Boolean visible;
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
    @Column(name = "status", nullable = false, length = 16)
    private String status;
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
''', encoding='utf-8', newline='\n')
(base/'repository/TenantFilterConfigRepository.java').write_text('''package com.dms.platform.config.repository;

import com.dms.platform.config.entity.TenantFilterConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TenantFilterConfigRepository extends JpaRepository<TenantFilterConfig, Long> {
    List<TenantFilterConfig> findByTenantIdAndPageKeyAndStatusOrderBySortOrderAsc(UUID tenantId, String pageKey, String status);
}
''', encoding='utf-8', newline='\n')
