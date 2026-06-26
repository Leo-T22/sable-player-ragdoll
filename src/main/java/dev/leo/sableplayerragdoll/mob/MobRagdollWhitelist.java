package dev.leo.sableplayerragdoll.mob;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;

public final class MobRagdollWhitelist {
   private static final String RESOURCE_PATH = "/sable_player_ragdoll/mob_ragdoll_whitelist.json";
   private static volatile Data data;

   private MobRagdollWhitelist() {
   }

   public static boolean isAllowed(EntityType<?> type) {
      ResourceLocation id = entityId(type);
      return id != null && isWhitelisted(id);
   }

   public static boolean isAllowed(ServerLevel level, EntityType<?> type) {
      ResourceLocation id = entityId(type);
      if (id == null) {
         return false;
      }
      if (level != null && MobRagdollBlacklistSavedData.get(level).isBlacklisted(id)) {
         return false;
      }
      return isWhitelisted(id);
   }

   private static boolean isWhitelisted(ResourceLocation id) {
      Data d = data();
      return d.allowAll() || d.entities().contains(id.toString()) || d.namespaces().contains(id.getNamespace());
   }

   private static ResourceLocation entityId(EntityType<?> type) {
      if (type == null) {
         return null;
      }
      ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
      if (id == null) {
         return null;
      }
      return id;
   }

   private static Data data() {
      Data local = data;
      if (local == null) {
         synchronized (MobRagdollWhitelist.class) {
            local = data;
            if (local == null) {
               local = load();
               data = local;
            }
         }
      }
      return local;
   }

   private static Data load() {
      try (InputStream in = MobRagdollWhitelist.class.getResourceAsStream(RESOURCE_PATH)) {
         if (in == null) {
            SablePlayerRagdoll.LOGGER.warn(
               "[sable_player_ragdoll] mob ragdoll whitelist {} not found; no mobs can be ragdolled", RESOURCE_PATH);
            return Data.EMPTY;
         }
         JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
         boolean allowAll = root.has("allow_all") && root.get("allow_all").getAsBoolean();
         Set<String> entities = readStringArray(root, "entities");
         Set<String> namespaces = readStringArray(root, "namespaces");
         SablePlayerRagdoll.LOGGER.info(
            "[sable_player_ragdoll] mob ragdoll whitelist loaded: allow_all={}, {} entities, {} namespaces",
            allowAll, entities.size(), namespaces.size());
         return new Data(allowAll, entities, namespaces);
      } catch (Exception error) {
         SablePlayerRagdoll.LOGGER.error(
            "[sable_player_ragdoll] failed to read mob ragdoll whitelist {}: {}", RESOURCE_PATH, error.toString());
         return Data.EMPTY;
      }
   }

   private static Set<String> readStringArray(JsonObject root, String key) {
      Set<String> values = new HashSet<>();
      if (root.has(key) && root.get(key).isJsonArray()) {
         JsonArray array = root.getAsJsonArray(key);
         for (JsonElement element : array) {
            String value = element.getAsString().trim();
            if (!value.isEmpty()) {
               values.add(value);
            }
         }
      }
      return values;
   }

   private record Data(boolean allowAll, Set<String> entities, Set<String> namespaces) {
      static final Data EMPTY = new Data(false, Set.of(), Set.of());
   }
}
