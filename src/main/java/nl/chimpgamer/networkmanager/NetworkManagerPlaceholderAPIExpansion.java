package nl.chimpgamer.networkmanager;

import com.google.common.collect.ImmutableMap;
import me.clip.placeholderapi.expansion.Configurable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Taskable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import nl.chimpgamer.networkmanager.api.NetworkManagerPlugin;
import nl.chimpgamer.networkmanager.api.NetworkManagerProvider;
import nl.chimpgamer.networkmanager.api.cache.CacheManager;
import nl.chimpgamer.networkmanager.api.cache.modules.CachedPlayers;
import nl.chimpgamer.networkmanager.api.models.player.Player;
import nl.chimpgamer.networkmanager.api.placeholders.PlaceholderHook;
import nl.chimpgamer.networkmanager.api.utils.TimeUtils;
import nl.chimpgamer.networkmanager.api.values.Message;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class NetworkManagerPlaceholderAPIExpansion extends PlaceholderExpansion implements Configurable, Taskable {
    private final AtomicReference<BukkitTask> cached = new AtomicReference<>();
    private NetworkManagerPlugin networkManager;
    private Map<String, Long> playtimeTop = Collections.synchronizedMap(new LinkedHashMap<>());

    @Override
    public boolean canRegister() {
        return Bukkit.getPluginManager().isPluginEnabled("NetworkManager");
    }

    @Override
    public boolean register() {
        if (canRegister()) {
            this.networkManager = NetworkManagerProvider.Companion.get();
            return super.register();
        }
        return false;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "networkmanager";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ChimpGamer";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.2.4";
    }

    @Override
    public String onPlaceholderRequest(org.bukkit.entity.Player bukkitPlayer, @NotNull String identifier) {
        if (bukkitPlayer == null) {
            return "Error: Player not found";
        }
        CacheManager cacheManager = this.networkManager.getCacheManager();
        CachedPlayers cachedPlayers = cacheManager.getCachedPlayers();
        Optional<Player> opPlayer = cachedPlayers.getPlayerSafe(bukkitPlayer.getUniqueId());
        if (!opPlayer.isPresent()) {
            return "Error: Player not found";
        }
        Player player = opPlayer.get();
        String[] temp;
        if (identifier.startsWith("playtime_top")) {
            temp = identifier.split("_");
            if (temp.length != 3) {
                return null;
            } else {
                String position = temp[2];
                if (position != null) {
                    if (Integer.parseInt(position) >= 11) {
                        return "Please define an number between 1 - 10";
                    }
                    if (playtimeTop.isEmpty()) {
                        playtimeTop = cachedPlayers.getTopPlayTimesUserName();
                    }

                    ArrayList<String> top10list = new ArrayList<>();

                    String playtimeTopMessage = this.networkManager.getMessage(player.getLanguage(), Message.PLAYTIME_TOP);

                    playtimeTop.forEach((key, value) -> {
                        Map<String, Object> replacements = ImmutableMap.<String, Object>builder()
                                .put("position", position)
                                .put("playername", key)
                                .put("playtime", TimeUtils.INSTANCE.getTimeString(player.getLanguage(), TimeUnit.MILLISECONDS.toSeconds(value)))
                                .put("playtime_h", String.valueOf(parseOnlineTimeHours(value)))
                                .put("playtime_m", String.valueOf(parseOnlineTimeMinutes(value)))
                                .put("playtime_s", String.valueOf(parseOnlineTimeSeconds(value)))
                                .build();

                        Component component = MiniMessage.miniMessage().deserialize(playtimeTopMessage, TagResolver.resolver(mapToTagResolvers(replacements)));

                        top10list.add(LegacyComponentSerializer.legacyAmpersand().serialize(component));
                    });
                    String message = top10list.get(Integer.parseInt(position) - 1);
                    return this.networkManager.format(message);
                }
            }
        } else {
            PlaceholderHook placeholderHook = networkManager.getPlaceholderManager().getPlaceholders().get("");
            return placeholderHook.onPlaceholderRequest(player, identifier);
        }
        return "Invalid placeholder.";
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Collections.singletonMap("playtime_top_update_interval", 60L);
    }

    @Override
    public void start() {
        BukkitTask task = Bukkit.getScheduler().runTaskLaterAsynchronously(getPlaceholderAPI(), () -> {
            playtimeTop.clear();
            playtimeTop.putAll(networkManager.getCacheManager().getCachedPlayers().getTopPlayTimesUserName());
        }, 20L * getLong("playtime_top_update_interval", 60L));
        BukkitTask previousTask = cached.getAndSet(task);
        if (previousTask != null) previousTask.cancel();
    }

    @Override
    public void stop() {
        BukkitTask previousTask = cached.getAndSet(null);
        if (previousTask != null) {
            previousTask.cancel();
        }
        playtimeTop.clear();
    }

    public long parseOnlineTimeHours(long milliseconds) {
        return TimeUnit.MILLISECONDS.toHours(milliseconds);
    }

    public long parseOnlineTimeMinutes(long milliseconds) {
        long result = milliseconds;
        long hours = TimeUnit.MILLISECONDS.toHours(result);
        result -= TimeUnit.HOURS.toMillis(hours);
        return TimeUnit.MILLISECONDS.toMinutes(result);
    }

    public long parseOnlineTimeSeconds(long milliseconds) {
        long result = milliseconds;
        long hours = TimeUnit.MILLISECONDS.toHours(result);
        result -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(result);
        result -= TimeUnit.MINUTES.toMillis(minutes);
        return TimeUnit.MILLISECONDS.toSeconds(result);
    }

    public List<TagResolver> mapToTagResolvers(Map<String, Object> map) {
        return map.entrySet().stream().map(entry -> {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof ComponentLike) {
                return Placeholder.component(key, (ComponentLike) value);
            } else {
                return Placeholder.unparsed(key, value.toString());
            }
        }).collect(Collectors.toList());
    }
}