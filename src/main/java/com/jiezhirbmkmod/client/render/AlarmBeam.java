package com.jiezhirbmkmod.client.render;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Beam geometry shared by the TESR and the screen-space spotlight pass.
 *
 * <p>This is the same maths the reference mod settled on after several rounds
 * of "the light patch is on the left but the beam is on the right": one place
 * computes the direction, everybody else reads it. The three mounting postures
 * get their own branch instead of one generic formula with patches.</p>
 *
 * <ul>
 *   <li><b>FLOOR</b>: radial sweep in the horizontal plane, tilted down a little;</li>
 *   <li><b>CEILING</b>: straight down along the lens axis, no sweep;</li>
 *   <li><b>WALL</b>: a single full circle hugging the wall, centre on the lens mouth.</li>
 * </ul>
 */
public final class AlarmBeam {

    /** World-space offset of the beam origin along the lens axis (blocks). */
    static final double BEAM_ORIGIN_Y = 0.5D;
    /** Tilt of the floor beam away from the radial plane: -5 deg, i.e. slightly down. */
    private static final double BEAM_PITCH = -5.0D;
    /** Extra downward bias so a floor lamp reaches the ground within a few blocks. */
    static final double BEAM_DROP = 0.20D;
    /** Must stay 0: a ceiling lamp shines straight down, no radial term. */
    static final double CEILING_RADIAL = 0.0D;
    /** In-wall "down" tangent, used as the falling base of the wall circle. */
    private static final Vec3d WALL_DOWN = new Vec3d(0.0D, -1.0D, 0.0D);

    private AlarmBeam() {
    }

    /** A unit vector perpendicular to {@code axis}. */
    public static Vec3d perpendicular(Vec3d axis) {
        Vec3d ref = Math.abs(axis.y) < 0.9D ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
        Vec3d p = axis.crossProduct(ref);
        double len = p.length();
        return len < 1.0E-6D ? new Vec3d(1, 0, 0) : p.scale(1.0D / len);
    }

    /** Unit radial direction inside the radial plane: {@code u*cos + v*sin}. */
    public static Vec3d radialUnit(Vec3d u, Vec3d v, double angleDeg) {
        double spin = Math.toRadians(angleDeg);
        return u.scale(Math.cos(spin)).add(v.scale(Math.sin(spin)));
    }

    /** Horizontal component of a vector, normalised; falls back to (0,0,1). */
    public static Vec3d horizontal(Vec3d v) {
        Vec3d flat = new Vec3d(v.x, 0.0D, v.z);
        double len = flat.length();
        if (len < 1.0E-6D) {
            return new Vec3d(0.0D, 0.0D, 1.0D);
        }
        return flat.scale(1.0D / len);
    }

    /** Beam origin in world space: block centre pushed out along the lens axis. */
    public static Vec3d beamMouth(BlockPos pos, Vec3d axis) {
        return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                .add(axis.scale(BEAM_ORIGIN_Y));
    }

    /**
     * World-space, normalised beam direction - the only source of truth for the
     * spotlights. Getting this wrong is what makes a light patch disagree with
     * the lit geometry, so do not duplicate it anywhere else.
     */
    public static Vec3d beamWorldDir(Vec3d axis, Vec3d u, Vec3d v, double angleDeg) {
        LightType type = LightType.classify(axis);
        Vec3d radial = radialUnit(u, v, angleDeg);
        switch (type) {
            case WALL: {
                // One complete circle on the wall: the centre sits on the lens
                // mouth plane and sin sweeps the spot up and down, so dir.y can
                // be positive. A downward offset here would pin the spot to the
                // lower half of the circle.
                Vec3d n = horizontal(axis);
                Vec3d t = horizontal(u);
                double ang = Math.toRadians(angleDeg);
                double radius = 0.5D;
                double seep = 0.2D;
                Vec3d targetOnWall = t.scale(radius * Math.cos(ang))
                        .add(WALL_DOWN.scale(radius * Math.sin(ang)));
                return targetOnWall.subtract(n.scale(seep)).normalize();
            }
            case CEILING: {
                return axis.add(radial.scale(CEILING_RADIAL)).normalize();
            }
            case FLOOR:
            default: {
                double pitch = Math.toRadians(BEAM_PITCH);
                Vec3d dir = radial.scale(Math.cos(pitch)).add(axis.scale(Math.sin(pitch)));
                return new Vec3d(dir.x, dir.y - BEAM_DROP, dir.z).normalize();
            }
        }
    }
}
