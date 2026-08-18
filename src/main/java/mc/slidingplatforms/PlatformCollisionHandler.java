package mc.slidingplatforms;

import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

public final class PlatformCollisionHandler {

    private PlatformCollisionHandler() {}

    private static final double EPS = 1.0E-7;

    private static final double SEARCH_PAD = 2.0;

    private static final double GLUE_BELOW = 0.3, GLUE_ABOVE = 0.35;

    private static final double SIDE_ENTRY_MAX = 0.35;

    private static final double ME_MAX_STEP = 0.4;

    private static final WeakHashMap<Entity, Vec3d> LAST_POS = new WeakHashMap<>();

    public static void handle(SlidingPlatformEntity platform, World world, Vec3d oldPlatformPos, Vec3d delta) {
        if (delta.lengthSquared() < 1.0E-12) return;
        Box wholeLocal = platform.getRelBox();
        List<Box> locals = platform.getLocalBoxes();
        if (wholeLocal == null || locals == null || locals.isEmpty()) return;

        double maxAbs = Math.max(Math.abs(delta.x), Math.max(Math.abs(delta.y), Math.abs(delta.z)));
        int steps = (int) Math.ceil(maxAbs / ME_MAX_STEP);
        if (steps <= 1) {
            handleStep(platform, world, wholeLocal, locals, oldPlatformPos, delta);
            return;
        }
        Vec3d per = new Vec3d(delta.x / steps, delta.y / steps, delta.z / steps);
        for (int i = 0; i < steps; i++) {
            handleStep(platform, world, wholeLocal, locals, oldPlatformPos.add(per.multiply(i)), per);
        }
    }

    private static void handleStep(SlidingPlatformEntity platform, World world, Box wholeLocal,
                                   List<Box> locals, Vec3d oldPlatformPos, Vec3d delta) {
        Vec3d newPlatformPos = oldPlatformPos.add(delta);
        Box wholeOld = wholeLocal.offset(oldPlatformPos);
        Box wholeNew = wholeLocal.offset(newPlatformPos);
        Box corridor = wholeOld.union(wholeNew);
        Box query = corridor.expand(SEARCH_PAD, SEARCH_PAD, SEARCH_PAD);

        double ax = Math.abs(delta.x), ay = Math.abs(delta.y), az = Math.abs(delta.z);
        Direction0 moveAxis = ax >= ay && ax >= az ? Direction0.X : (ay >= az ? Direction0.Y : Direction0.Z);

        List<Entity> riders = new ArrayList<>();

        for (Entity e : world.getEntitiesByClass(Entity.class, query,
                candidate -> canCollideWith(platform, candidate))) {

            Vec3d curPos = e.getPos();
            Vec3d oldPos = LAST_POS.getOrDefault(e, curPos);
            Box oldEntityBox = e.getBoundingBox().contract(EPS, EPS, EPS)
                    .offset(oldPos.subtract(curPos));

            Box eb = e.getBoundingBox().contract(EPS, EPS, EPS);
            double bestRoof = Double.NEGATIVE_INFINITY;
            for (Box local : locals) {
                Box boxOld = local.offset(oldPlatformPos);

                if (eb.maxX > boxOld.minX && eb.minX < boxOld.maxX
                        && eb.maxZ > boxOld.minZ && eb.minZ < boxOld.maxZ) {
                    double feet = e.getY();
                    if (feet >= boxOld.maxY - GLUE_BELOW && feet <= boxOld.maxY + GLUE_ABOVE) {

                        double newRoof = boxOld.maxY + delta.y;
                        if (newRoof > bestRoof) bestRoof = newRoof;
                    }
                }
            }
            boolean jumping = e.getVelocity().y > 0.12;
            if (bestRoof > Double.NEGATIVE_INFINITY && !jumping) {
                e.setPosition(e.getX(), bestRoof, e.getZ());
                e.setVelocity(e.getVelocity().x, 0.0, e.getVelocity().z);
                e.setOnGround(true);
                e.fallDistance = 0.0f;
                if (!riders.contains(e)) riders.add(e);
            }

            if (!riders.contains(e)) {
                eb = e.getBoundingBox().contract(EPS, EPS, EPS);
                Box overlapNew = firstOverlap(eb, locals, newPlatformPos);
                if (overlapNew != null) {

                    boolean wasIn = firstOverlap(oldEntityBox, locals, oldPlatformPos) != null;
                    if (!wasIn && moveAxis != Direction0.Y) {

                        if (trySideExit(e, eb, overlapNew, moveAxis)) {
                            LAST_POS.put(e, e.getPos());
                            continue;
                        }
                    }

                    if (moveAxis == Direction0.Y && delta.y > 0.0
                            && isSideBrush(eb, overlapNew, delta)
                            && pushOutHorizontally(e, eb, overlapNew)) {
                        LAST_POS.put(e, e.getPos());
                        continue;
                    }

                    e.move(MovementType.SHULKER, delta);

                    for (int pass = 0; pass < 3; pass++) {
                        eb = e.getBoundingBox().contract(EPS, EPS, EPS);
                        Box still = firstOverlap(eb, locals, newPlatformPos);
                        if (still == null) break;
                        clipToLeadingFace(e, eb, still, delta, moveAxis);
                    }
                }
            }

            if (riders.contains(e) && (delta.x != 0.0 || delta.z != 0.0)) {
                e.move(MovementType.SHULKER, new Vec3d(delta.x, 0.0, delta.z));
            }

            if (riders.contains(e) && (delta.x != 0.0 || delta.z != 0.0)) {
                eb = e.getBoundingBox().contract(EPS, EPS, EPS);
                double feetY = e.getY();
                boolean onAny = false;
                double best = Double.MAX_VALUE, bx = 0.0, bz = 0.0;
                for (Box local : locals) {
                    Box boxNew = local.offset(newPlatformPos);

                    if (feetY < boxNew.maxY - GLUE_BELOW - 0.5
                            || feetY > boxNew.maxY + GLUE_ABOVE) continue;
                    if (eb.maxX > boxNew.minX && eb.minX < boxNew.maxX
                            && eb.maxZ > boxNew.minZ && eb.minZ < boxNew.maxZ) {
                        onAny = true;
                        break;
                    }

                    double px = eb.maxX <= boxNew.minX ? boxNew.minX + 0.01 - eb.maxX
                            : (eb.minX >= boxNew.maxX ? boxNew.maxX - 0.01 - eb.minX : 0.0);
                    double pz = eb.maxZ <= boxNew.minZ ? boxNew.minZ + 0.01 - eb.maxZ
                            : (eb.minZ >= boxNew.maxZ ? boxNew.maxZ - 0.01 - eb.minZ : 0.0);
                    double d2 = px * px + pz * pz;
                    if (d2 < best) { best = d2; bx = px; bz = pz; }
                }
                if (!onAny && best <= 0.3 * 0.3) {
                    e.move(MovementType.SHULKER, new Vec3d(bx, 0.0, bz));
                }

                if (e instanceof net.minecraft.server.network.ServerPlayerEntity) {
                    e.setVelocity(Vec3d.ZERO);
                }
            }

            LAST_POS.put(e, e.getPos());
        }
    }

    private static boolean canCollideWith(SlidingPlatformEntity platform, Entity e) {        return e != platform
                && !(e instanceof SlidingPlatformEntity)
                && !e.isSpectator()
                && !e.noClip
                && !e.hasVehicle()
                && e.getPistonBehavior() == PistonBehavior.NORMAL;
    }

    public static void groundRiders(net.minecraft.client.world.ClientWorld world) {
        for (Entity raw : world.getEntities()) {
            if (!(raw instanceof SlidingPlatformEntity platform) || !platform.isTravelling()) continue;
            List<Box> locals = platform.getLocalBoxes();
            Box rel = platform.getRelBox();
            if (rel == null || locals.isEmpty()) continue;
            Vec3d pos = platform.getPos();
            Box query = rel.offset(pos).expand(SEARCH_PAD, 1.0, SEARCH_PAD);
            for (Entity e : world.getOtherEntities(null, query,
                    ent -> canCollideWith(platform, ent))) {
                if (e.getVelocity().y > 0.12) continue;
                Box eb = e.getBoundingBox().contract(EPS, EPS, EPS);
                double feet = e.getY();
                for (Box local : locals) {
                    Box col = local.offset(pos);
                    if (eb.maxX > col.minX && eb.minX < col.maxX
                            && eb.maxZ > col.minZ && eb.minZ < col.maxZ
                            && feet >= col.maxY - GLUE_BELOW && feet <= col.maxY + GLUE_ABOVE) {

                        if (Math.abs(feet - col.maxY) > 1.0e-4) {
                            e.setPosition(e.getX(), col.maxY, e.getZ());
                            e.setVelocity(e.getVelocity().x, 0.0, e.getVelocity().z);
                        }
                        e.setOnGround(true);
                        e.fallDistance = 0.0f;
                        break;
                    }
                }
            }
        }
    }

    private enum Direction0 { X, Y, Z }

    private static boolean trySideExit(Entity e, Box eb, Box corridor, Direction0 moveAxis) {
        if (moveAxis == Direction0.X) {

            double outMin = eb.maxZ - corridor.minZ;
            double outMax = corridor.maxZ - eb.minZ;
            double m = Math.min(outMin, outMax);
            if (m < 0.0 || m > SIDE_ENTRY_MAX) return false;
            double w = e.getWidth();
            if (outMin <= outMax) e.setPosition(e.getX(), e.getY(), corridor.minZ - w / 2.0);
            else                  e.setPosition(e.getX(), e.getY(), corridor.maxZ + w / 2.0);
            e.setVelocity(e.getVelocity().x, e.getVelocity().y, 0.0);
            return true;
        }

        double outMin = eb.maxX - corridor.minX;
        double outMax = corridor.maxX - eb.minX;
        double m = Math.min(outMin, outMax);
        if (m < 0.0 || m > SIDE_ENTRY_MAX) return false;
        double w = e.getWidth();
        if (outMin <= outMax) e.setPosition(corridor.minX - w / 2.0, e.getY(), e.getZ());
        else                  e.setPosition(corridor.maxX + w / 2.0, e.getY(), e.getZ());
        e.setVelocity(0.0, e.getVelocity().y, e.getVelocity().z);
        return true;
    }

    private static Box firstOverlap(Box eb, List<Box> locals, Vec3d platformPos) {
        for (Box local : locals) {
            Box world = local.offset(platformPos);
            if (eb.intersects(world)) return world;
        }
        return null;
    }

    private static boolean isSideBrush(Box eb, Box blockNew, Vec3d delta) {
        double oldTop = blockNew.maxY - delta.y;
        return eb.minY < oldTop - GLUE_BELOW;
    }

    private static boolean pushOutHorizontally(net.minecraft.entity.Entity e, Box eb, Box box) {
        double west = eb.maxX - box.minX;
        double east = box.maxX - eb.minX;
        double north = eb.maxZ - box.minZ;
        double south = box.maxZ - eb.minZ;
        double m = Math.min(Math.min(west, east), Math.min(north, south));
        if (m < 0.0 || m > SIDE_ENTRY_MAX) return false;
        double w = e.getWidth() / 2.0 + 0.001;
        if (m == west)       e.setPosition(box.minX - w, e.getY(), e.getZ());
        else if (m == east)  e.setPosition(box.maxX + w, e.getY(), e.getZ());
        else if (m == north) e.setPosition(e.getX(), e.getY(), box.minZ - w);
        else                 e.setPosition(e.getX(), e.getY(), box.maxZ + w);
        return true;
    }

    private static void clipToLeadingFace(Entity e, Box eb, Box wholeNew, Vec3d delta, Direction0 moveAxis) {
        double w = e.getWidth(), h = e.getHeight();
        Vec3d p = e.getPos();
        switch (moveAxis) {
            case X -> {
                double x = delta.x > 0 ? wholeNew.maxX + w / 2.0 : wholeNew.minX - w / 2.0;
                e.setPosition(x, p.y, p.z);
                e.setVelocity(0.0, e.getVelocity().y, e.getVelocity().z);
            }
            case Y -> {
                double y = delta.y > 0 ? wholeNew.maxY : wholeNew.minY - h;
                e.setPosition(p.x, y + (delta.y > 0 ? 0.001 : -0.001), p.z);
                e.setVelocity(e.getVelocity().x, 0.0, e.getVelocity().z);
            }
            case Z -> {
                double z = delta.z > 0 ? wholeNew.maxZ + w / 2.0 : wholeNew.minZ - w / 2.0;
                e.setPosition(p.x, p.y, z);
                e.setVelocity(e.getVelocity().x, e.getVelocity().y, 0.0);
            }
        }
    }

    private static void ejectOutside(Entity e, Box eb, Box whole, double platformDy) {
        double toMinX = eb.maxX - whole.minX;
        double toMaxX = whole.maxX - eb.minX;
        double toMinZ = eb.maxZ - whole.minZ;
        double toMaxZ = whole.maxZ - eb.minZ;
        double toTop = platformDy >= -0.05 ? whole.maxY - eb.minY : Double.MAX_VALUE;
        double toBottom = platformDy <= 0.05 ? eb.maxY - whole.minY : Double.MAX_VALUE;

        double best = Math.min(Math.min(toMinX, toMaxX),
                       Math.min(Math.min(toMinZ, toMaxZ), Math.min(toTop, toBottom)));
        if (best == Double.MAX_VALUE) return;

        Vec3d p = e.getPos();
        double w = eb.maxX - eb.minX;
        double h = eb.maxY - eb.minY;
        if (best == toTop)          e.setPosition(p.x, whole.maxY + 0.02, p.z);
        else if (best == toBottom)  e.setPosition(p.x, whole.minY - h - 0.02, p.z);
        else if (best == toMinX)    e.setPosition(whole.minX - w / 2.0 - 0.02, p.y, p.z);
        else if (best == toMaxX)    e.setPosition(whole.maxX + w / 2.0 + 0.02, p.y, p.z);
        else if (best == toMinZ)    e.setPosition(p.x, p.y, whole.minZ - w / 2.0 - 0.02);
        else                        e.setPosition(p.x, p.y, whole.maxZ + w / 2.0 + 0.02);
    }
}
