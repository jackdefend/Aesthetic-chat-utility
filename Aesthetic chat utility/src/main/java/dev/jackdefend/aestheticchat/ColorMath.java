package dev.jackdefend.aestheticchat;

import net.minecraft.world.scores.TeamColor;

public final class ColorMath {
    private ColorMath() {}

    /** The closest of the 16 base colors, by perceptual (CIELAB) distance. */
    public static TeamColor nearestBase(int rgb) {
        return nearestIn(TeamColor.VALUES, rgb);
    }

    /** The closest color to rgb among the given base colors. */
    public static TeamColor nearestIn(java.util.List<TeamColor> pool, int rgb) {
        double[] target = lab(rgb);
        TeamColor best = pool.get(0);
        double bestDist = Double.MAX_VALUE;
        for (TeamColor c : pool) {
            double[] l = lab(c.rgb());
            double dl = target[0] - l[0], da = target[1] - l[1], db = target[2] - l[2];
            double d = dl * dl + da * da + db * db;
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    private static double[] lab(int rgb) {
        double r = lin(((rgb >> 16) & 0xFF) / 255.0);
        double g = lin(((rgb >> 8) & 0xFF) / 255.0);
        double b = lin((rgb & 0xFF) / 255.0);
        double x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047;
        double y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b);
        double z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double lin(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double f(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116.0;
    }
}
