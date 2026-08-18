package mc.slidingplatforms;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScreenRegistry extends PersistentState {

    private static final String STATE_ID = "slidingplatforms_screen_registry";

    private static final Map<RegistryKey<World>, Map<BlockPos, String>> MAP = new HashMap<>();

    private static final Pattern AUTO_NAME = Pattern.compile("^(?:Экран|Screen) (\\d+)$");

    public record Entry(BlockPos pos, String name) {}

    private static ScreenRegistry state(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager().getOrCreate(
                ScreenRegistry::fromNbt, ScreenRegistry::new, STATE_ID);
    }

    public static ScreenRegistry fromNbt(NbtCompound nbt) {
        ScreenRegistry r = new ScreenRegistry();
        MAP.clear();
        NbtList list = nbt.getList("screens", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound e = list.getCompound(i);
            try {
                RegistryKey<World> dim = RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD,
                        new Identifier(e.getString("dim")));
                BlockPos pos = BlockPos.fromLong(e.getLong("pos"));
                MAP.computeIfAbsent(dim, k -> new HashMap<>()).put(pos, e.getString("name"));
            } catch (Exception ignored) {  }
        }
        return r;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        synchronized (ScreenRegistry.class) {
            MAP.forEach((dim, m) -> m.forEach((pos, name) -> {
                NbtCompound e = new NbtCompound();
                e.putString("dim", dim.getValue().toString());
                e.putLong("pos", pos.asLong());
                e.putString("name", name);
                list.add(e);
            }));
        }
        nbt.put("screens", list);
        return nbt;
    }

    public static synchronized String ensureName(ServerWorld world, BlockPos pos, String current) {
        return ensureName(world, pos, current, "Экран");
    }

    public static synchronized String ensureName(ServerWorld world, BlockPos pos, String current, String word) {
        Map<BlockPos, String> m = MAP.computeIfAbsent(world.getRegistryKey(), k -> new HashMap<>());
        String existing = m.get(pos);
        if (existing != null) return existing;

        String name = (current != null && !current.isBlank()) ? current : smallestFreeName(word);
        m.put(pos, name);
        state(world).markDirty();
        return name;
    }

    public static synchronized void rename(ServerWorld world, BlockPos pos, String name) {
        if (name == null || name.isBlank()) return;
        name = name.trim();
        if (name.length() > 24) name = name.substring(0, 24);
        MAP.computeIfAbsent(world.getRegistryKey(), k -> new HashMap<>()).put(pos, name);
        state(world).markDirty();
    }

    public static synchronized void unregister(World world, BlockPos pos) {
        Map<BlockPos, String> m = MAP.get(world.getRegistryKey());
        if (m != null && m.remove(pos) != null && world instanceof ServerWorld sw) {
            state(sw).markDirty();
        }
    }

    public static synchronized List<Entry> list(World world) {
        Map<BlockPos, String> m = MAP.get(world.getRegistryKey());
        List<Entry> out = new ArrayList<>();
        if (m != null) m.forEach((p, n) -> out.add(new Entry(p, n)));
        out.sort(Comparator.comparing(Entry::name));
        return out;
    }

    public static synchronized @org.jetbrains.annotations.Nullable String nameOf(World world, BlockPos pos) {
        Map<BlockPos, String> m = MAP.get(world.getRegistryKey());
        return m == null ? null : m.get(pos);
    }

    private static synchronized String smallestFreeName(String word) {
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (Map<BlockPos, String> m : MAP.values()) {
            for (String n : m.values()) {
                Matcher match = AUTO_NAME.matcher(n);
                if (match.matches()) used.add(Integer.parseInt(match.group(1)));
            }
        }
        int n = 1;
        while (used.contains(n)) n++;
        return word + " " + n;
    }
}
