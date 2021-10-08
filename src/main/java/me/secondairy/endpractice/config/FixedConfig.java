package me.secondairy.endpractice.config;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FixedConfig {

    private Map<String, Float> playerAttributes;

    private List<InventoryItemEntry> inventory;

    @NotNull
    public Map<String, Float> getPlayerAttributes() {
        return playerAttributes;
    }

    @NotNull
    public List<InventoryItemEntry> getInventory() {
        return inventory;
    }

    @NotNull
    public List<InventoryItemEntry> getUniqueItems() {
        return inventory
                .stream()
                .filter(item -> item.isUnique() && item.isEditable())
                .collect(Collectors.toList());
    }

    @NotNull
    public List<InventoryItemEntry> getNonUniqueItems() {
        return inventory
                .stream()
                .filter(item -> !(item.isUnique() && item.isEditable()))
                .collect(Collectors.toList());
    }
}
