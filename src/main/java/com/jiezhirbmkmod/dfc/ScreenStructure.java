package com.jiezhirbmkmod.dfc;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public final class ScreenStructure {
    public static final int COLS = 4;
    public static final int ROWS = 3;
    public static final int PARTS = 12;
    public static final int TILT_STEPS = 5;
    public static final int TILT_STEP_DEG = 15;
    public static final int TILT_DEFAULT = 2;

    private ScreenStructure() {
    }

    public static int part(int col, int row) {
        return row * 4 + col;
    }

    public static int colOf(int part) {
        return part % 4;
    }

    public static int rowOf(int part) {
        return part / 4;
    }

    public static boolean isValidPart(int part) {
        return part >= 0 && part < 12;
    }

    public static EnumFacing rightOf(EnumFacing facing) {
        if (facing == null || facing.getAxis() == EnumFacing.Axis.Y) {
            return EnumFacing.EAST;
        }
        return facing.rotateY();
    }

    public static BlockPos partPos(BlockPos origin, int part, EnumFacing facing) {
        if (origin == null || !ScreenStructure.isValidPart(part)) {
            return null;
        }
        EnumFacing right = ScreenStructure.rightOf(facing);
        return origin.offset(right, ScreenStructure.colOf(part)).offset(EnumFacing.DOWN, ScreenStructure.rowOf(part));
    }

    public static BlockPos originOf(BlockPos pos, int part, EnumFacing facing) {
        if (pos == null || !ScreenStructure.isValidPart(part)) {
            return null;
        }
        EnumFacing right = ScreenStructure.rightOf(facing);
        return pos.offset(right, -ScreenStructure.colOf(part)).offset(EnumFacing.UP, ScreenStructure.rowOf(part));
    }

    public static float yawOf(EnumFacing facing) {
        if (facing == null) {
            return 0.0f;
        }
        switch (facing) {
            case SOUTH: {
                return 180.0f;
            }
            case EAST: {
                return 270.0f;
            }
            case WEST: {
                return 90.0f;
            }
        }
        return 0.0f;
    }

    public static float[] rotateY(float x, float y, float z, EnumFacing facing) {
        double rad = Math.toRadians(ScreenStructure.yawOf(facing));
        double c = Math.cos(rad);
        double s = Math.sin(rad);
        return new float[]{(float)((double)x * c + (double)z * s), y, (float)((double)(-x) * s + (double)z * c)};
    }

    public static float[] rotateX(float x, float y, float z, float deg) {
        double rad = Math.toRadians(deg);
        double c = Math.cos(rad);
        double s = Math.sin(rad);
        return new float[]{x, (float)((double)y * c - (double)z * s), (float)((double)y * s + (double)z * c)};
    }

    public static float[] tiltedNormal(float tiltDeg) {
        return ScreenStructure.rotateX(0.0f, 0.0f, -1.0f, tiltDeg);
    }

    public static float[] localOf(int part) {
        return new float[]{(float)ScreenStructure.colOf(part) - 1.5f, 1.0f - (float)ScreenStructure.rowOf(part), 0.0f};
    }

    public static double[] anchor(EnumFacing facing) {
        EnumFacing right = ScreenStructure.rightOf(facing);
        return new double[]{0.5 + (double)(right.getXOffset() * 3) / 2.0, -0.5, 0.5 + (double)(right.getZOffset() * 3) / 2.0};
    }

    public static int tiltFromPitch(float pitch) {
        int t = Math.round((pitch + 30.0f) / 15.0f);
        if (t < 0) {
            t = 0;
        }
        if (t > 4) {
            t = 4;
        }
        return t;
    }

    public static float tiltDegrees(int tilt) {
        int t = tilt;
        if (t < 0) {
            t = 0;
        }
        if (t > 4) {
            t = 4;
        }
        return (float)t * 15.0f;
    }

    public static float panelWidthBlocks() {
        return 4.0f;
    }

    public static float panelHeightBlocks() {
        return 3.0f;
    }

    public static float[] panelUv() {
        return new float[]{1.0f, 0.0f, 1.0f, 0.0f};
    }
}

