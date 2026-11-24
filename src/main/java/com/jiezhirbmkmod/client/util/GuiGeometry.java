package com.jiezhirbmkmod.client.util;

public final class GuiGeometry {
    private GuiGeometry() {
    }

    public static float rowInset(float radius, float height, int row) {
        if (radius <= 0.0f || height <= 0.0f || row < 0 || row >= (int)Math.ceil(height)) {
            return 0.0f;
        }
        double dy = Math.min((double)row + 0.5, (double)height - ((double)row + 0.5));
        if (dy >= (double)radius) {
            return 0.0f;
        }
        double d = (double)radius - dy;
        double inner = (double)(radius * radius) - d * d;
        if (inner <= 0.0) {
            return radius;
        }
        return (float)((double)radius - Math.sqrt(inner));
    }

    public static double coveredArea(float width, float height, float radius) {
        double r = Math.max(0.0f, Math.min(radius, Math.min(width, height) / 2.0f));
        double area = 0.0;
        int rows = (int)Math.ceil(height);
        for (int i = 0; i < rows; ++i) {
            double w = (double)width - 2.0 * (double)GuiGeometry.rowInset((float)r, height, i);
            if (!(w > 0.0)) continue;
            area += w;
        }
        return area;
    }
}

