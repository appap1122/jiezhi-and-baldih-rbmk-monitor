package com.jiezhirbmkmod.item;

public final class JetHeat {
    public static final double MAX_BURN_SECONDS = 18.0;
    public static final int MAX_HEAT = 360;
    public static final int UNLOCK_HEAT = 108;
    public static final int HEAT_PER_TICK = 1;
    public static final int COOL_PER_TICK = 2;
    private State state = State.COOLING;
    private int heat = 0;

    public State getState() {
        return this.state;
    }

    public int getHeat() {
        return this.heat;
    }

    public float getHeatRatio() {
        return (float)this.heat / 360.0f;
    }

    public State tick(boolean wantFire) {
        if (this.state == State.OVERHEATED) {
            this.heat = Math.max(0, this.heat - 2);
            if (this.heat <= 108) {
                this.state = State.COOLING;
            }
            return this.state;
        }
        if (wantFire) {
            this.heat = Math.min(360, this.heat + 1);
            this.state = this.heat >= 360 ? State.OVERHEATED : State.FIRING;
        } else {
            this.heat = Math.max(0, this.heat - 2);
            this.state = State.COOLING;
        }
        return this.state;
    }

    public boolean canFire() {
        return this.state != State.OVERHEATED;
    }

    public boolean isFiring() {
        return this.state == State.FIRING;
    }

    public boolean isOverheated() {
        return this.state == State.OVERHEATED;
    }

    public double remainingSeconds() {
        if (this.state == State.OVERHEATED) {
            return 0.0;
        }
        return (double)this.heat / 1.0 / 20.0;
    }

    public void setHeat(int heat) {
        this.heat = Math.max(0, Math.min(360, heat));
    }

    public void setState(State state) {
        this.state = state == null ? State.COOLING : state;
    }

    public void reset() {
        this.heat = 0;
        this.state = State.COOLING;
    }

    public static enum State {
        COOLING,
        FIRING,
        OVERHEATED;

    }
}

