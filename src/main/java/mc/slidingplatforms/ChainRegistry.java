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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChainRegistry extends PersistentState {

    private static final String STATE_ID = "slidingplatforms_chain_registry";
    private static final Pattern AUTO_NAME = Pattern.compile("^Цепочка (\\d+)$");

    private static final Map<RegistryKey<World>, Map<String, Chain>> MAP = new HashMap<>();

    private static final class Chain {
        String name = "";
        final LinkedHashSet<String> members = new LinkedHashSet<>();
        final LinkedHashMap<String, Integer> floorNums = new LinkedHashMap<>();
    }

    public record Entry(String id, String name) {}

    private static ChainRegistry state(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager().getOrCreate(
                ChainRegistry::fromNbt, ChainRegistry::new, STATE_ID);
    }

    public static ChainRegistry fromNbt(NbtCompound nbt) {
        ChainRegistry r = new ChainRegistry();
        MAP.clear();
        NbtList list = nbt.getList("chains", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound e = list.getCompound(i);
            try {
                RegistryKey<World> dim = RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD,
                        new Identifier(e.getString("dim")));
                Chain c = new Chain();
                c.name = e.getString("name");
                NbtList mem = e.getList("members", NbtElement.STRING_TYPE);
                for (int j = 0; j < mem.size(); j++) c.members.add(mem.getString(j));
                NbtList nums = e.getList("floors", NbtElement.COMPOUND_TYPE);
                for (int j = 0; j < nums.size(); j++) {
                    NbtCompound f = nums.getCompound(j);
                    c.floorNums.put(f.getString("ctrl"), f.getInt("num"));
                }
                MAP.computeIfAbsent(dim, k -> new HashMap<>()).put(e.getString("id"), c);
            } catch (Exception ignored) {  }
        }
        return r;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        synchronized (ChainRegistry.class) {
            MAP.forEach((dim, m) -> m.forEach((id, c) -> {
                NbtCompound e = new NbtCompound();
                e.putString("dim", dim.getValue().toString());
                e.putString("id", id);
                e.putString("name", c.name);
                NbtList mem = new NbtList();
                for (String s : c.members) mem.add(net.minecraft.nbt.NbtString.of(s));
                e.put("members", mem);
                NbtList nums = new NbtList();
                c.floorNums.forEach((ctrl, num) -> {
                    NbtCompound f = new NbtCompound();
                    f.putString("ctrl", ctrl);
                    f.putInt("num", num);
                    nums.add(f);
                });
                e.put("floors", nums);
                list.add(e);
            }));
        }
        nbt.put("chains", list);
        return nbt;
    }

    private static @Nullable Chain get(World w, String id) {
        Map<String, Chain> m = MAP.get(w.getRegistryKey());
        return m == null ? null : m.get(id);
    }

    private static Map<String, Chain> chains(ServerWorld sw) {
        return MAP.computeIfAbsent(sw.getRegistryKey(), k -> new HashMap<>());
    }

    public static synchronized @Nullable String nameOf(World w, String id) {
        Chain c = get(w, id);
        return c == null ? null : c.name;
    }

    public static synchronized List<String> members(World w, String id) {
        Chain c = get(w, id);
        return c == null ? List.of() : new ArrayList<>(c.members);
    }

    public static synchronized List<Entry> list(World w) {
        Map<String, Chain> m = MAP.get(w.getRegistryKey());
        List<Entry> out = new ArrayList<>();
        if (m != null) m.forEach((id, c) -> out.add(new Entry(id, c.name)));
        out.sort(Comparator.comparing(Entry::name));
        return out;
    }

    public static synchronized String setName(World w, String id, String desired) {
        if (!(w instanceof ServerWorld sw)) return "";
        Chain c = chains(sw).computeIfAbsent(id, k -> new Chain());
        String n = (desired == null || desired.isBlank()) ? smallestFreeName() : desired.trim();
        if (n.length() > 24) n = n.substring(0, 24);
        if (!n.equals(c.name)) {
            c.name = n;
            state(sw).markDirty();
        }
        return n;
    }

    public static synchronized void join(World w, String id, String key) {
        Chain c = get(w, id);
        if (c == null) return;
        if (c.members.add(key) && w instanceof ServerWorld sw) state(sw).markDirty();
    }

    public static synchronized void leave(World w, String id, String key) {
        if (!(w instanceof ServerWorld sw)) return;
        Map<String, Chain> m = MAP.get(w.getRegistryKey());
        Chain c = m == null ? null : m.get(id);
        if (c == null) return;
        boolean changed = c.members.remove(key);
        if (c.members.isEmpty() && c.floorNums.isEmpty()) {
            m.remove(id);
            changed = true;
        }
        if (changed) state(sw).markDirty();
    }

    public static synchronized int numberFor(World w, String id, String ctrlKey) {
        Chain c = get(w, id);
        if (c == null) return 1;
        Integer n = c.floorNums.get(ctrlKey);
        if (n != null) return n;
        int max = 0;
        for (int v : c.floorNums.values()) max = Math.max(max, v);
        c.floorNums.put(ctrlKey, max + 1);
        if (w instanceof ServerWorld sw) state(sw).markDirty();
        return max + 1;
    }

    public static synchronized void setFloorNumber(World w, String id, String ctrlKey, int num) {
        if (!(w instanceof ServerWorld sw)) return;
        Chain c = get(w, id);
        if (c == null) return;
        num = Math.max(1, Math.min(num, 99));
        int old = numberFor(w, id, ctrlKey);
        if (old == num) return;
        for (Map.Entry<String, Integer> e : c.floorNums.entrySet()) {
            if (e.getValue() == num && !e.getKey().equals(ctrlKey)) {
                e.setValue(old);
                break;
            }
        }
        c.floorNums.put(ctrlKey, num);
        state(sw).markDirty();
    }

    public static synchronized void forgetFloor(World w, String id, String ctrlKey) {
        Chain c = get(w, id);
        if (c != null && c.floorNums.remove(ctrlKey) != null && w instanceof ServerWorld sw) {
            state(sw).markDirty();
        }
    }

    public static synchronized @Nullable String chainOfScreen(World w, String screenKey) {
        Map<String, Chain> m = MAP.get(w.getRegistryKey());
        if (m == null) return null;
        for (Map.Entry<String, Chain> e : m.entrySet()) {
            if (e.getValue().members.contains(screenKey)) return e.getKey();
        }
        return null;
    }

    private static synchronized String smallestFreeName() {
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (Map<String, Chain> m : MAP.values()) {
            for (Chain c : m.values()) {
                Matcher match = AUTO_NAME.matcher(c.name == null ? "" : c.name);
                if (match.matches()) used.add(Integer.parseInt(match.group(1)));
            }
        }
        int n = 1;
        while (used.contains(n)) n++;
        return "Цепочка " + n;
    }
}
