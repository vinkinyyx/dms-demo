/*
 * Native SQL paging helpers for controllers that do not use Spring Data Pageable.
 */
package com.dms.common.util;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;

public final class PagingUtil {
    private PagingUtil() {}

    public static int normalizePage(int page) {
        if (page < 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "页码从 1 开始");
        }
        return page;
    }

    public static int normalizeSize(int size) {
        if (size < 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "每页数量至少 1");
        }
        return Math.min(size, 1000);
    }

    public static int offset(int page, int size) {
        return (normalizePage(page) - 1) * normalizeSize(size);
    }
}
