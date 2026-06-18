package dev.leo.sableplayerragdoll.mob.block;

import net.minecraft.util.StringRepresentable;

public enum MobPartRole implements StringRepresentable {
    HEAD("head"),
    TORSO("torso"),
    ARM("arm"),
    LEG("leg"),
    WING("wing"),
    TAIL("tail"),
    OTHER("other");

    private final String serializedName;

    MobPartRole(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }

    public static MobPartRole byName(String name) {
        for (MobPartRole role : values()) {
            if (role.serializedName.equals(name)) {
                return role;
            }
        }
        return OTHER;
    }
}
