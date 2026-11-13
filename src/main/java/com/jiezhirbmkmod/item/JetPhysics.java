package com.jiezhirbmkmod.item;

public final class JetPhysics {
    public static final double GRAVITY_PER_TICK = 0.08;
    public static final double VERTICAL_DRAG = 0.98;
    public static final double HORIZONTAL_DRAG = 0.91;
    public static final double HOVER_V = 3.92;
    public static final double RISE_V = 4.55;
    public static final double FWD_SPEED = 0.46;
    public static final double STRAFE_SPEED = 0.34;
    public static final double RISE_BONUS = 0.3;
    public static final double VERTICAL_ACCEL = 0.28;
    public static final double HORIZONTAL_ACCEL = 0.18;

    public static double steerVertical(double currentV, boolean rising) {
        double maxDelta;
        double target = rising ? 4.55 : 3.92;
        double delta = target - currentV;
        if (delta > (maxDelta = 0.28)) {
            delta = maxDelta;
        } else if (delta < -maxDelta) {
            delta = -maxDelta;
        }
        return currentV + delta;
    }

    public static double steerHorizontal(double current, double target) {
        double delta = target - current;
        double maxDelta = 0.18;
        if (delta > maxDelta) {
            delta = maxDelta;
        } else if (delta < -maxDelta) {
            delta = -maxDelta;
        }
        return current + delta;
    }

    public static double[] worldTarget(double yawDeg, double forward, double strafe, boolean rising) {
        double len = Math.sqrt(forward * forward + strafe * strafe);
        if (len > 1.0) {
            forward /= len;
            strafe /= len;
        }
        double f = forward * (0.46 + (rising ? 0.3 : 0.0));
        double s = strafe * 0.34;
        double yaw = Math.toRadians(yawDeg);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        return new double[]{s * cos - f * sin, f * cos + s * sin};
    }

    public static double[] step(double[] current, double yawDeg, double forward, double strafe, double up, boolean rising) {
        double[] target = JetPhysics.worldTarget(yawDeg, forward, strafe, rising);
        double vx = JetPhysics.steerHorizontal(current[0], target[0]);
        double vz = JetPhysics.steerHorizontal(current[2], target[1]);
        double vy = JetPhysics.steerVertical(current[1] + up * 0.28, rising);
        return new double[]{vx, vy, vz};
    }
}

