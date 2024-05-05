package com.cavetale.magicmap;

public enum RenderType {
    SURFACE,
    CAVE,
    NETHER,
    ;

    public String getHumanName() {
        return name().substring(0, 1) + name().substring(1).toLowerCase();
    }
}
