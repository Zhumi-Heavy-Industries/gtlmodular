package com.gtlmodular.common.machine.nanochip;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;

import java.util.concurrent.locks.Lock;

/** gtlcore 的结构匹配锁在正常路径直接释放；用 finally 保证匹配抛异常时仍能释放锁，避免后续检查永久阻塞。 */
public final class NanochipPatternLockHardening {

    private NanochipPatternLockHardening() {
    }

    /** 直接调用 checkPattern，避免与机器的带锁覆写相互递归。 */
    public static boolean withLock(MultiblockControllerMachine machine, boolean lockFixEnabled) {
        if (!lockFixEnabled) {
            return machine.checkPatternWithLock();
        }
        final Lock lock = machine.getPatternLock();
        lock.lock();
        try {
            return machine.checkPattern();
        } finally {
            lock.unlock();
        }
    }

    /** 异步匹配占用锁时直接返回，让调用线程继续运行并在下一轮重试。 */
    public static boolean withTryLock(MultiblockControllerMachine machine, boolean lockFixEnabled) {
        if (!lockFixEnabled) {
            return machine.checkPatternWithTryLock();
        }
        final Lock lock = machine.getPatternLock();
        if (!lock.tryLock()) {
            return false;
        }
        try {
            return machine.checkPattern();
        } finally {
            lock.unlock();
        }
    }
}
