package com.dms.v4;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
public class V4CalcResult {
    private List<V4Line> lines;
    private List<String> promotionMessages;

    public static V4CalcResult of(List<V4Line> lines, List<String> messages) {
        return new V4CalcResult(lines, messages == null ? new ArrayList<>() : messages);
    }
}
