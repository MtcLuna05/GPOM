package com.l.gpom.compat.openblocks;

public final class OpenBlocksTankRenderCompat {
    private static final double FLUID_INSET = 1.0D / 512.0D;

    private OpenBlocksTankRenderCompat() {
    }

    public static double insetBoundaryCoordinate(double coordinate) {
        if (coordinate == 0.0D) {
            return FLUID_INSET;
        }
        if (coordinate == 1.0D) {
            return 1.0D - FLUID_INSET;
        }
        return coordinate;
    }
}
