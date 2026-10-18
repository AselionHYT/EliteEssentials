package com.eliteessentials.services;

import com.eliteessentials.model.Home;
import com.eliteessentials.model.Location;
import com.eliteessentials.model.PlayerFile;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.storage.PlayerStorageProvider;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Service for managing player homes.
 * Supports permission-based home limits via:
 * - eliteessentials.command.home.limit.<number> (1-20, 40, 60, 80, 100)
 * - eliteessentials.command.home.limit.unlimited
 */
public class HomeService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    private final PlayerStorageProvider storage;

    public HomeService(PlayerStorageProvider storage) {
        this.storage = storage;
    }

    /**
     * Result of a home operation.
     */
    public enum Result {
        SUCCESS,
        HOME_NOT_FOUND,
        LIMIT_REACHED,
        INVALID_NAME,
        NAME_TAKEN
    }

    /**
     * Set a home for a player at their current location.
     * 
     * @param playerId Player UUID
     * @param name Home name
     * @param location Player's current location
     * @return Result of the operation
     */
    public Result setHome(UUID playerId, String name, Location location) {
        if (name == null || name.isBlank()) {
            return Result.INVALID_NAME;
        }

        String normalizedName = name.toLowerCase().trim();
        PlayerFile playerFile = storage.getPlayer(playerId);
        if (playerFile == null) {
            return Result.INVALID_NAME;
        }
        
        // Overwriting a home in the same budget never counts against the limit; worlds listed
        // in homes.worldHomeLimits have their own budget (HomeLimits).
        Map<String, Integer> worldLimits = worldHomeLimits();
        String world = location == null ? null : location.getWorld();
        int sharedMax = HomeLimits.bucketOf(world, worldLimits) == null ? getMaxHomes(playerId) : 0;
        if (HomeLimits.wouldExceed(playerFile.getHomes(), normalizedName, world, worldLimits, sharedMax)) {
            return Result.LIMIT_REACHED;
        }

        Home home = new Home(normalizedName, location);
        playerFile.setHome(home);
        
        // Persist immediately to disk so data isn't lost on crash
        storage.saveAndMarkDirty(playerId);
        
        logger.info("Player " + playerId + " set home '" + normalizedName + "' at " + location);
        return Result.SUCCESS;
    }

    /**
     * Get a home location for a player.
     */
    public Optional<Home> getHome(UUID playerId, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        PlayerFile playerFile = storage.getPlayer(playerId);
        if (playerFile == null) {
            return Optional.empty();
        }
        return playerFile.getHome(name.toLowerCase().trim());
    }

    /**
     * Delete a home for a player.
     */
    public Result deleteHome(UUID playerId, String name) {
        if (name == null || name.isBlank()) {
            return Result.INVALID_NAME;
        }
        
        PlayerFile playerFile = storage.getPlayer(playerId);
        if (playerFile == null) {
            return Result.HOME_NOT_FOUND;
        }
        
        boolean deleted = playerFile.deleteHome(name.toLowerCase().trim());
        if (!deleted) {
            return Result.HOME_NOT_FOUND;
        }
        
        // Persist the change to disk
        storage.saveAndMarkDirty(playerId);
        
        logger.info("Player " + playerId + " deleted home '" + name + "'");
        return Result.SUCCESS;
    }

    /**
     * Rename a home for a player.
     */
    public Result renameHome(UUID playerId, String oldName, String newName) {
        if (oldName == null || oldName.isBlank() || newName == null || newName.isBlank()) {
            return Result.INVALID_NAME;
        }

        PlayerFile playerFile = storage.getPlayer(playerId);
        if (playerFile == null) {
            return Result.HOME_NOT_FOUND;
        }

        String oldKey = oldName.toLowerCase().trim();
        String newKey = newName.toLowerCase().trim();

        Optional<Home> homeOpt = playerFile.getHome(oldKey);
        if (homeOpt.isEmpty()) {
            return Result.HOME_NOT_FOUND;
        }

        if (!oldKey.equals(newKey) && playerFile.hasHome(newKey)) {
            return Result.NAME_TAKEN;
        }

        if (oldKey.equals(newKey)) {
            return Result.SUCCESS;
        }

        Home home = homeOpt.get();
        home.setName(newKey);
        playerFile.deleteHome(oldKey);
        playerFile.setHome(home);

        storage.saveAndMarkDirty(playerId);

        logger.info("Player " + playerId + " renamed home '" + oldKey + "' to '" + newKey + "'");
        return Result.SUCCESS;
    }

    /**
     * Get all homes for a player.
     */
    public Map<String, Home> getHomes(UUID playerId) {
        PlayerFile playerFile = storage.getPlayer(playerId);
        if (playerFile == null) {
            return Map.of();
        }
        return playerFile.getHomes();
    }

    /**
     * Get all home names for a player.
     */
    public Set<String> getHomeNames(UUID playerId) {
        PlayerFile playerFile = storage.getPlayer(playerId);
        if (playerFile == null) {
            return Set.of();
        }
        return playerFile.getHomeNames();
    }

    /**
     * Get the number of homes a player has.
     */
    public int getHomeCount(UUID playerId) {
        PlayerFile playerFile = storage.getPlayer(playerId);
        if (playerFile == null) {
            return 0;
        }
        return playerFile.getHomeCount();
    }

    /**
     * Get the maximum number of homes allowed for a player.
     * Checks permissions in this order:
     * 1. eliteessentials.limit.homes.unlimited - returns Integer.MAX_VALUE
     * 2. eliteessentials.limit.homes.<number> - returns highest matching number
     * 3. Config default (homes.maxHomes)
     * 
     * @param playerId Player UUID
     * @return Maximum homes allowed
     */
    public int getMaxHomes(UUID playerId) {
        return PermissionService.get().getMaxHomes(playerId);
    }

    /**
     * Maximum homes for a player in a world: the world's own limit from homes.worldHomeLimits,
     * else {@link #getMaxHomes(UUID)}.
     */
    public int getMaxHomes(UUID playerId, String world) {
        Map<String, Integer> worldLimits = worldHomeLimits();
        if (HomeLimits.bucketOf(world, worldLimits) != null) {
            return HomeLimits.limitFor(world, worldLimits, 0);
        }
        return getMaxHomes(playerId);
    }

    /** Number of homes that count against {@link #getMaxHomes(UUID)} (homes in limited worlds excluded). */
    public int getSharedHomeCount(UUID playerId) {
        PlayerFile playerFile = storage.getPlayer(playerId);
        if (playerFile == null) {
            return 0;
        }
        return HomeLimits.countIn(playerFile.getHomes(), null, worldHomeLimits());
    }

    private static Map<String, Integer> worldHomeLimits() {
        com.eliteessentials.EliteEssentials plugin = com.eliteessentials.EliteEssentials.getInstance();
        if (plugin == null || plugin.getConfigManager() == null) {
            return Map.of();
        }
        Map<String, Integer> limits = plugin.getConfigManager().getConfig().homes.worldHomeLimits;
        return limits == null ? Map.of() : limits;
    }

    /**
     * Save all home data to disk.
     */
    public void save() {
        storage.saveAll();
    }
}
