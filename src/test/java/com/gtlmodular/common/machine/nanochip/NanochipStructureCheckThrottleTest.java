package com.gtlmodular.common.machine.nanochip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁住节流间隔与上游相位过滤的关系。
 *
 * 上游 MultiblockControllerMachine.asyncCheckPattern 只在
 * (hasError() || !isFormed) && ((getOffset() + periodID) % 4 == 0) 时重跑匹配；
 * MultiblockWorldSavedData 每 250 ms 把 periodID 加 1，每个控制器各调一次。
 * 我们的闸门每 FORMED_SKIP 次才放行一次，放行时的 periodID 因此以 FORMED_SKIP 为步长。
 * 步长与 4 不互质时，可达相位只是模 4 的一个真子集，一部分已成形机器的零件表永远灌不回来。
 */
class NanochipStructureCheckThrottleTest {

    /** 上游那条相位过滤写死的就是 4，改上游之前这里不动。 */
    private static final int UPSTREAM_PHASE_PERIOD = 4;

    @Test
    void formedSkipMustBeCoprimeWithUpstreamPhasePeriod() {
        assertEquals(1, gcd(NanochipStructureCheckThrottle.FORMED_SKIP, UPSTREAM_PHASE_PERIOD),
                "FORMED_SKIP=" + NanochipStructureCheckThrottle.FORMED_SKIP
                        + " 与上游的 " + UPSTREAM_PHASE_PERIOD
                        + " 相位过滤不互质：放行的轮次在模 " + UPSTREAM_PHASE_PERIOD
                        + " 下只覆盖部分相位，一部分机器读档后永远等不到重新匹配，零件表一直为空。");
    }

    /** 互质还不够直白，这里按闸门的实际算法（++seen % skip == 0）走一遍相位覆盖。 */
    @Test
    void formedGateEventuallyReachesEveryUpstreamPhase() {
        int skip = NanochipStructureCheckThrottle.FORMED_SKIP;
        boolean[] reached = new boolean[UPSTREAM_PHASE_PERIOD];
        long seen = 0;
        for (long periodID = 0; periodID < 64; periodID++) {
            if ((++seen % skip) == 0) {
                reached[(int) (periodID % UPSTREAM_PHASE_PERIOD)] = true;
            }
        }
        for (int phase = 0; phase < UPSTREAM_PHASE_PERIOD; phase++) {
            assertTrue(reached[phase],
                    "相位 " + phase + " 在 64 轮内一次都没被放行（FORMED_SKIP=" + skip + "），"
                            + "该相位的机器读档后会一直卡在零件表为空。");
        }
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }
}
