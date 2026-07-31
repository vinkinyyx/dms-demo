package com.dms.common.enums;

import lombok.Getter;

@Getter
public enum OperationAction {
    CREATE("鍒涘缓", "鏂板鍗曟嵁"),
    UPDATE("鏇存柊", "淇敼鍗曟嵁"),
    DELETE("鍒犻櫎", "鍒犻櫎鍗曟嵁"),
    APPROVE("瀹℃牳閫氳繃", "瀹℃牳閫氳繃璁㈠崟"),
    REJECT("驳回", "驳回单据"),
    CANCEL("取消", "取消单据");
    
    private final String label;
    private final String description;
    
    OperationAction(String label, String description) {
        this.label = label;
        this.description = description;
    }
}
