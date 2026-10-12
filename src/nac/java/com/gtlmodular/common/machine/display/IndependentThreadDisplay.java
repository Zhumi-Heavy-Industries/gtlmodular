package com.gtlmodular.common.machine.display;

/** 显示端只接受显式接入的独立批次，不能从模组命名空间推断加工方式。 */
public interface IndependentThreadDisplay {
    ThreadDisplaySnapshot threadDisplaySnapshot();
}
