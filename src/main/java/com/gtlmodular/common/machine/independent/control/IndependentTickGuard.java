package com.gtlmodular.common.machine.independent.control;

public final class IndependentTickGuard {
    private long tick = Long.MIN_VALUE;
    private boolean cancelled;
    private boolean executing;
    private boolean startsBlocked;

    public boolean repeated(long now) { return executing || tick == now; }
    public boolean cancelRepeated() { return executing || cancelled; }
    public void begin(long now) { tick = now; passThrough(); }
    public void take() { cancelled = true; startsBlocked = true; }
    public void enter() { executing = true; take(); }
    public void exit() { executing = false; }
    public boolean executing() { return executing; }
    public boolean blocksStarts() { return executing || startsBlocked; }
    public void blockStarts(boolean blocked) { startsBlocked = blocked; }
    public void passThrough() { cancelled = false; startsBlocked = false; }
}
