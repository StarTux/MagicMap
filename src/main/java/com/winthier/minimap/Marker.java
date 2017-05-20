package com.winthier.minimap;

import lombok.Data;

@Data
public class Marker {
    private String name;
    private String world;
    private int x, z;
    private String message;
}
