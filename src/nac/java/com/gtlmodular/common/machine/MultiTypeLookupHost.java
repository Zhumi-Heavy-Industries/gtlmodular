package com.gtlmodular.common.machine;

/** 多类型搜索需要保留非激活类型的冷槽，否则这些槽无法首次匹配；用只读契约避免绑定上游逻辑类。 */
public interface MultiTypeLookupHost {

    /** 实现须只读且不抛异常；返回 true 时调用方须放开单类型收窄，避免饿死非激活类型的冷槽。 */
    boolean searchesAllRecipeTypes();
}
