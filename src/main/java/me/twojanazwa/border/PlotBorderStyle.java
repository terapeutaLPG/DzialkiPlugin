package me.twojanazwa.border;

import java.util.Arrays;

import org.bukkit.Material;

public enum PlotBorderStyle {
    SPRING("spring", "Spring", "Szczescie", Material.GLOWSTONE_DUST),
    CLOUDY("cloudy", "Cloudy", "Mgliste", Material.SNOWBALL),
    NATURE("nature", "Nature", "Natura", Material.FLOWERING_AZALEA),
    ARCANE("arcane", "Arcane", "Magia", Material.ENCHANTED_BOOK),
    CRYSTAL("crystal", "Crystal", "Krysztaly", Material.AMETHYST_SHARD),
    FROST("frost", "Frost", "Lod", Material.PACKED_ICE),
    GOLD("gold", "Fortune", "Bogactwo", Material.GOLD_NUGGET),
    SHADOW("shadow", "Shadow", "Dym", Material.BLACK_DYE);

    private final String id;
    private final String displayName;
    private final String particleLabel;
    private final Material icon;

    PlotBorderStyle(String id, String displayName, String particleLabel, Material icon) {
        this.id = id;
        this.displayName = displayName;
        this.particleLabel = particleLabel;
        this.icon = icon;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getParticleLabel() {
        return particleLabel;
    }

    public Material getIcon() {
        return icon;
    }

    public static PlotBorderStyle byId(String id) {
        if (id == null || id.isBlank()) {
            return CLOUDY;
        }
        return Arrays.stream(values())
                .filter(style -> style.id.equalsIgnoreCase(id))
                .findFirst()
                .orElse(CLOUDY);
    }
}
