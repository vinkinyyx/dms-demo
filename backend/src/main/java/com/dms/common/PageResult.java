/*
 * 分页返回结构 DTO，屏蔽 Spring Data Page 内部结构，提供 total/page/size/list。
 */
package com.dms.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private Long total;
    private Integer page;
    private Integer size;
    private List<T> list;

    public static <T> PageResult<T> of(Page<T> page) {
        return new PageResult<>(
                page.getTotalElements(),
                page.getNumber() + 1,
                page.getSize(),
                page.getContent()
        );
    }

    public static <T> PageResult<T> empty(Integer page, Integer size) {
        return new PageResult<>(0L, page, size, Collections.emptyList());
    }
}
