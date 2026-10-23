package com.gtlmodular.common.machine.independent.recipe;

/** 机器可以把并行上限报到 long；适配器只在机器实现该接口时使用，避免把 int 上限复制到引擎里。 */
public interface LongParallelMachine {

    long getMaxParallelLong();
}
