package dev.leo.sableplayerragdoll.mob;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class MobRagdollBlacklistSavedData extends SavedData {
   private static final String DATA_NAME = "sable_player_ragdoll_mob_ragdoll_blacklist";
   private static final SavedData.Factory<MobRagdollBlacklistSavedData> FACTORY = new SavedData.Factory<>(
      MobRagdollBlacklistSavedData::new, MobRagdollBlacklistSavedData::load, null
   );

   private final Set<String> entities = new LinkedHashSet<>();
   private final Set<String> namespaces = new LinkedHashSet<>();

   public static MobRagdollBlacklistSavedData get(ServerLevel level) {
      return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
   }

   public boolean isBlacklisted(ResourceLocation entityId) {
      return entityId != null && (this.entities.contains(entityId.toString()) || this.namespaces.contains(entityId.getNamespace()));
   }

   public boolean addEntity(ResourceLocation entityId) {
      if (entityId == null) {
         return false;
      }
      boolean changed = this.entities.add(entityId.toString());
      if (changed) {
         setDirty();
      }
      return changed;
   }

   public boolean removeEntity(ResourceLocation entityId) {
      if (entityId == null) {
         return false;
      }
      boolean changed = this.entities.remove(entityId.toString());
      if (changed) {
         setDirty();
      }
      return changed;
   }

   public boolean addNamespace(String namespace) {
      String normalized = normalizeNamespace(namespace);
      if (!isValidNamespace(normalized)) {
         return false;
      }
      boolean changed = this.namespaces.add(normalized);
      if (changed) {
         setDirty();
      }
      return changed;
   }

   public boolean removeNamespace(String namespace) {
      String normalized = normalizeNamespace(namespace);
      if (!isValidNamespace(normalized)) {
         return false;
      }
      boolean changed = this.namespaces.remove(normalized);
      if (changed) {
         setDirty();
      }
      return changed;
   }

   public boolean clear() {
      if (this.entities.isEmpty() && this.namespaces.isEmpty()) {
         return false;
      }
      this.entities.clear();
      this.namespaces.clear();
      setDirty();
      return true;
   }

   public Set<String> entities() {
      return Set.copyOf(this.entities);
   }

   public Set<String> namespaces() {
      return Set.copyOf(this.namespaces);
   }

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
      tag.put("Entities", stringList(this.entities));
      tag.put("Namespaces", stringList(this.namespaces));
      return tag;
   }

   public static boolean isValidNamespace(String namespace) {
      if (namespace == null || namespace.isBlank()) {
         return false;
      }
      for (int i = 0; i < namespace.length(); i++) {
         char c = namespace.charAt(i);
         if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
            continue;
         }
         return false;
      }
      return true;
   }

   public static String normalizeNamespace(String namespace) {
      return namespace == null ? "" : namespace.trim().toLowerCase(Locale.ROOT);
   }

   private static MobRagdollBlacklistSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
      MobRagdollBlacklistSavedData data = new MobRagdollBlacklistSavedData();
      readStrings(tag, "Entities", data.entities);
      readStrings(tag, "Namespaces", data.namespaces);
      return data;
   }

   private static ListTag stringList(Collection<String> values) {
      ListTag list = new ListTag();
      values.stream().sorted().forEach(value -> list.add(StringTag.valueOf(value)));
      return list;
   }

   private static void readStrings(CompoundTag tag, String key, Set<String> target) {
      ListTag list = tag.getList(key, Tag.TAG_STRING);
      for (int i = 0; i < list.size(); i++) {
         String value = list.getString(i).trim();
         if (!value.isEmpty()) {
            target.add(value);
         }
      }
   }
}
