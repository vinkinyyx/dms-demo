/*
 * 分页查询请求 DTO，包含页码、每页大小与排序表达式。
 */
package com.dms.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

@Getter
@Setter
public class PageQuery {

    @Min(value = 1, message = "页码从 1 起")
    private Integer page = 1;

    @Min(value = 1, message = "每页数量至少 1")
    @Max(value = 1000, message = "每页数量不超过 1000")
    private Integer size = 20;

    /**
     * 排序表达式：field,asc|desc，多字段用分号分隔，例如 "createdAt,desc;id,asc"
     */
    private String sort;

    public Pageable toPageable() {
        Sort s = Sort.unsorted();
        if (StringUtils.hasText(sort)) {
            String[] segments = sort.split(";");
            for (String seg : segments) {
                String[] parts = seg.split(",");
                if (parts.length == 0 || !StringUtils.hasText(parts[0])) {
                    continue;
                }
                Sort.Direction dir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                        ? Sort.Direction.DESC : Sort.Direction.ASC;
                s = s.and(Sort.by(dir, parts[0].trim()));
            }
        }
        return PageRequest.of(Math.max(page - 1, 0), size, s);
    }
}
