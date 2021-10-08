package me.secondairy.endpractice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.StringReader;
import me.secondairy.endpractice.config.*;
import me.secondairy.endpractice.mixin.common.HungerManagerAccessor;
import me.secondairy.endpractice.mixin.common.ServerPlayerEntityAccessor;
import me.secondairy.endpractice.config.ItemNotFoundException;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Wearable;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.recipe.Recipe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Endpractice {
    public static final String VERSION = FabricLoader.getInstance().getModContainer("endpractice").get().getMetadata().getVersion().getFriendlyString();

//	I would like to use this first implementation as it is the suggested and recommended way of doing things with fabric.
//	Unfortunately, it has strange behaviour in my dev environment I don't have the time to trouble shoot
//	public static final Path CONFIG_FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve("endpractice.json");

    public static final Path CONFIG_FILE_PATH = Paths.get("config/endpractice-" + VERSION + ".json").toAbsolutePath();

    public static final boolean IS_CLIENT = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;

    private static final Logger logger = LogManager.getLogger("Endpractice");
    private static final Set<Item> requiredItems = new HashSet<>();
    public static EndpracticeConfig config;
    private static FixedConfig fixedConfig;
    private static boolean newWorld = false;
    private static Set<UUID> initializedPlayers;
    private static MinecraftServer MS;
    private static Random randomInstance;
    private static List<InventoryItemEntry> uniqueFixedConfigItems;
    private static List<InventoryItemEntry> nonUniqueFixedConfigItems;
    private static Map<String, Float> playerAttributes;

    private static final float spawnYaw = 0;
    private static final BlockPos spawnPos = new BlockPos(100, 49, 0);

    public static void log(Level level, String message) {
        logger.log(level, "[Endpractice v" + VERSION + "] " + message);
    }

    public static void playerLog(Level level, String message, @NotNull ServerPlayerEntity serverPlayerEntity) {
        log(level, "[" + serverPlayerEntity.getEntityName() + "] " + message);
    }

    public static boolean isNewWorld() {
        return newWorld;
    }

    public static void setNewWorld(boolean newWorld) {
        Endpractice.newWorld = newWorld;
    }

    @NotNull
    public static Set<UUID> getInitializedPlayers() {
        return initializedPlayers;
    }

    public static void setInitializedPlayers(Set<UUID> initializedPlayers) {
        Endpractice.initializedPlayers = initializedPlayers;
    }

    private static void clearInitializedPlayers() {
        setInitializedPlayers(new HashSet<>());
    }

    @NotNull
    public static MinecraftServer getMS() {
        return MS;
    }

    public static void setMS(MinecraftServer ms) {
        setInitializedPlayers(new HashSet<>());
        MS = ms;
    }

    @NotNull
    private static ServerWorld getOverworld() {
        return Objects.requireNonNull(getMS().getOverworld());
    }

    @NotNull
    private static ServerWorld getEnd() {
        return Objects.requireNonNull(getMS().getWorld(World.END));
    }

    @NotNull
    private static Random newRandomInstance() {
        long rawSeed = getOverworld().getSeed();
        String rawSeedString = Long.toString(rawSeed);
        long seed;
        StringBuilder seedString = new StringBuilder();

        /*
		 This drops every second digit from the world seed and uses the result as the random seed for all RNG in the mod
		 It's a measure to combat a potential divine travel esque situation.
		 */
        for (int i = 0; i < rawSeedString.length(); i += 2) {
            seedString.append(rawSeedString.charAt(i));
            seedString.append("0");
        }

        try {
            seed = Long.parseLong(seedString.toString());
        } catch (NumberFormatException e) {
            log(Level.INFO, "Unable to drop digits from seed. Using complete world seed.");
            seed = rawSeed;
        }

        Random returnRandom = new Random(seed);
        int j = returnRandom.nextInt(50) + 50;
        for (int i = 0; i < j; i++) {
            returnRandom.nextInt();
        }

        return returnRandom;
    }

    private static void resetRandoms() {
        randomInstance = newRandomInstance();
        log(Level.INFO, "Reset randoms using world seed");
    }


    public static void onInitialize() {
        log(Level.INFO, "Using Endpractice v" + Endpractice.VERSION + " by Secondairy!");
    }

    public static void readFixedConfigs() {
        fixedConfig = new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(
                Endpractice.class.getResourceAsStream("/fixed_config.json"))), FixedConfig.class);

        uniqueFixedConfigItems = fixedConfig.getUniqueItems();
        nonUniqueFixedConfigItems = fixedConfig.getNonUniqueItems();

        playerAttributes = fixedConfig.getPlayerAttributes();

        log(Level.INFO, "Loaded fixed configs");
    }

    private static void readConfig() throws FileNotFoundException {
        config = new Gson().fromJson(new FileReader(CONFIG_FILE_PATH.toFile()), EndpracticeConfig.class);
    }

    private static void saveConfig() {
        try {
            config = new EndpracticeConfig();

            config.setInventory(uniqueFixedConfigItems
                    .stream()
                    .map(item -> new UserConfigInventoryItemEntry(item.getName(), item.getPrettySlot()))
                    .collect(Collectors.toList())
            );

            PrintWriter writer = new PrintWriter(CONFIG_FILE_PATH.toFile());
            writer.print("");
            writer.close();

            Files.write(CONFIG_FILE_PATH, new GsonBuilder().setPrettyPrinting().create().toJson(config).getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void manageConfigs() throws FileNotFoundException {
        try {
            readConfig();
            if (!config.matches(uniqueFixedConfigItems)) {
                throw new MalformedConfigException("User config is setup wrong!");
            }
        } catch (Exception e) {
            log(Level.WARN, "Config file not found, new one being written.");
            e.printStackTrace();
            saveConfig();
            readConfig();
        }
    }

    public static void refreshConfigs() {
        try {
            manageConfigs();
        } catch (Exception e) {
            log(Level.FATAL, "Unable to initialize Config. This is a fatal error, please make a report on the GitHub.");
            e.printStackTrace();
        }
    }

    public static void commonConfigHandler() {
        try {
            readFixedConfigs();
            manageConfigs();
            log(Level.INFO, "Initialized Config");
        } catch (Exception e) {
            log(Level.FATAL, "Unable to initialize Config. This is a fatal error, please make a report on the GitHub.");
            e.printStackTrace();
        }
    }

    @Nullable
    private static ItemStack getItemStackFromName(String name) {
        name = Objects.requireNonNull(name).toLowerCase();
        String finalName = name;
        try {
            Item item = Registry.ITEM
                    .getOrEmpty(new Identifier(name))
                    .orElseThrow(() -> new ItemNotFoundException("Item " + finalName + " not found in registry!"));
            requiredItems.add(item);

            return new ItemStack(item);
        } catch (Exception e) {
            e.printStackTrace();
            log(Level.ERROR, "Unable to find the Item type " + name + ", please double check your config. Replaced with empty slot.");
            return null;
        }
    }

    private static boolean applyItemStack(
            @NotNull String name,
            @Nullable String tags,
            int count,
            @Nullable Integer damage,
            int slot,
            @NotNull ServerPlayerEntity serverPlayerEntity
    ) {
        try {
            if (count > 0) {
                if (slot >= -1 && slot <= 40) {
                    ItemStack itemStack = getItemStackFromName(name);

                    if (Objects.isNull(itemStack)) return false;

                    if (itemStack.isStackable()) {
                        itemStack.setCount(count);
                    }

                    if (!Objects.isNull(tags)) {
                        itemStack.setTag((new StringNbtReader(new StringReader(tags))).parseCompoundTag());
                    }

                    if (!Objects.isNull(damage) && itemStack.isDamageable()) {
                        itemStack.setDamage(damage);
                    }

                    if (slot >= 36 && slot <= 39) {
                        if (!(itemStack.getItem() instanceof Wearable)) {
                            playerLog(Level.ERROR, "Item " + name + " is not wearable! Cannot put into an armor slot", serverPlayerEntity);
                            return false;
                        }

                        serverPlayerEntity.inventory.armor.set(MobEntity.getPreferredEquipmentSlot(itemStack).getEntitySlotId(), itemStack.copy());
                    } else if (slot == 40) {
                        serverPlayerEntity.inventory.offHand.set(0, itemStack.copy());
                    } else {
                        serverPlayerEntity.inventory.insertStack(slot, itemStack.copy());
                    }

                    Criteria.INVENTORY_CHANGED.trigger(serverPlayerEntity, serverPlayerEntity.inventory, itemStack);

                    return true;
                } else {
                    playerLog(Level.ERROR, "Item " + name + " has not been configured for a valid slot!", serverPlayerEntity);
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            playerLog(Level.ERROR, "Unable to apply item " + name, serverPlayerEntity);
            return false;
        }
    }

    private static void setPlayerInventory(ServerPlayerEntity serverPlayerEntity) {
        stopAdvancementDisplay(serverPlayerEntity);

        Map<String, Integer> userConfigItems = config.getItems();
        boolean uniqueItemsFailure = uniqueFixedConfigItems
                .stream()
                .map(item -> applyItemStack(
                        item.getName(),
                        item.getTags(),
                        item.getCount(randomInstance),
                        item.getDamage(),
                        userConfigItems.getOrDefault(item.getName(), item.getPrettySlot()) - 1,
                        serverPlayerEntity)
                )
                .collect(Collectors.toSet())
                .contains(false);

        boolean nonUniqueItemsFailure = nonUniqueFixedConfigItems
                .stream()
                .map(item -> applyItemStack(
                        item.getName(),
                        item.getTags(),
                        item.getCount(randomInstance),
                        item.getDamage(),
                        item.getSlot(),
                        serverPlayerEntity)
                )
                .collect(Collectors.toSet())
                .contains(false);

        if (uniqueItemsFailure || nonUniqueItemsFailure) {
            serverPlayerEntity.sendMessage(new LiteralText("One or more items were not successfully applied. Double check your config.").formatted(Formatting.LIGHT_PURPLE), true);
            playerLog(Level.ERROR, "One or more items were not successfully applied. Double check your config", serverPlayerEntity);
        }

        startAdvancementDisplay(serverPlayerEntity);
        playerLog(Level.INFO, "Overwrote player inventory with configured items", serverPlayerEntity);
    }

    private static void openRecipeBook(ServerPlayerEntity serverPlayerEntity) {
        if (config.isRecipeBookEnabled()) {
            serverPlayerEntity.getRecipeBook().setGuiOpen(true);
            serverPlayerEntity.getRecipeBook().setFilteringCraftable(true);

            playerLog(Level.INFO, "Opened recipe book", serverPlayerEntity);
        }
    }

    private static void unlockRecipes(ServerPlayerEntity serverPlayerEntity) {
        List<Recipe<?>> recipesToUnlock = Objects.requireNonNull(getMS().getRecipeManager().values())
                .parallelStream()
                .filter(recipe -> requiredItems.contains(recipe.getOutput().getItem()))
                .collect(Collectors.toList());
        serverPlayerEntity.unlockRecipes(recipesToUnlock);

        playerLog(Level.INFO, "Unlocked recipes", serverPlayerEntity);
    }

    private static void stopAdvancementDisplay(ServerPlayerEntity serverPlayerEntity) {
        serverPlayerEntity.world.getGameRules().get(GameRules.ANNOUNCE_ADVANCEMENTS).set(false, null);
    }

    private static void startAdvancementDisplay(ServerPlayerEntity serverPlayerEntity) {
        serverPlayerEntity.world.getGameRules().get(GameRules.ANNOUNCE_ADVANCEMENTS).set(true, null);
    }

    private static void sendtoEnd(ServerPlayerEntity serverPlayerEntity) {
        serverPlayerEntity.sendMessage(new LiteralText("Please wait for chunks at target to be generated. Don't open your inventory.").formatted(Formatting.RED), true);

        serverPlayerEntity.refreshPositionAndAngles(spawnPos, spawnYaw, 0);
        serverPlayerEntity.setVelocity(Vec3d.ZERO);


        playerLog(Level.INFO, "Attemping spawn at " + spawnPos.toShortString() + " with yaw " + serverPlayerEntity.yaw, serverPlayerEntity);

        serverPlayerEntity.changeDimension(getEnd());

        serverPlayerEntity.sendMessage(new LiteralText(""), true);

        playerLog(Level.INFO, "Sent to the end", serverPlayerEntity);
    }

    private static void setSpawnPoint(ServerPlayerEntity serverPlayerEntity) {
        serverPlayerEntity.setSpawnPoint(serverPlayerEntity.world.getRegistryKey(), serverPlayerEntity.getBlockPos(), true, false);
        playerLog(Level.INFO, "Set spawnpoint to portal", serverPlayerEntity);
    }

    private static void disableSpawnInvulnerability(ServerPlayerEntity serverPlayerEntity) {
        ((ServerPlayerEntityAccessor) serverPlayerEntity).setJoinInvulnerabilityTicks(0);
        playerLog(Level.INFO, "Disabled spawn invulnerability", serverPlayerEntity);
    }

    private static void setPlayerAttributes(ServerPlayerEntity serverPlayerEntity) {
        if (playerAttributes.get("health") < 20.0F) {
            serverPlayerEntity.setHealth(playerAttributes.get("health"));
        }
        if (playerAttributes.get("hunger") < 20.0F) {
            serverPlayerEntity.getHungerManager().setFoodLevel(Math.round(playerAttributes.get("hunger")));
        }
        if (playerAttributes.get("saturation") != 5.0F) {
            ((HungerManagerAccessor) serverPlayerEntity.getHungerManager()).setFoodSaturationLevel(playerAttributes.get("saturation"));
        }
        playerLog(Level.INFO, "Set player attributes", serverPlayerEntity);
    }

    public static void onServerJoin(@NotNull ServerPlayerEntity serverPlayerEntity) {
        if (isNewWorld() && getInitializedPlayers().add(serverPlayerEntity.getUuid())) {
            playerLog(Level.INFO, "Player connected and recognised", serverPlayerEntity);

            sendtoEnd(serverPlayerEntity);
            setSpawnPoint(serverPlayerEntity);
            setPlayerInventory(serverPlayerEntity);
            openRecipeBook(serverPlayerEntity);
            unlockRecipes(serverPlayerEntity);
            setPlayerAttributes(serverPlayerEntity);
            disableSpawnInvulnerability(serverPlayerEntity);

            playerLog(Level.INFO, "Finished server side actions", serverPlayerEntity);
        } else {
            playerLog(Level.INFO, "Endpractice will not handle player", serverPlayerEntity);
        }
    }

    public static void onWorldGenStart() {
        boolean worldIsNew = getOverworld().getTime() == 0;
        setNewWorld(worldIsNew);

        clearInitializedPlayers();

        if (worldIsNew) {
            resetRandoms();
        }
        log(Level.INFO, worldIsNew ? "Detected creation of a new world" : "Detected reopening of a previously created world");
    }

    public static void onWorldGenComplete() {
        if (isNewWorld()) {
            log(Level.INFO, "World gen is complete");
        }
    }
}
