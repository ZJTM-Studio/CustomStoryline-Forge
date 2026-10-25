package com.yang458.customstoryline.data;

public class PerspectiveConfig {
    public boolean enabled;
    public float yaw;
    public float pitch;

    public PerspectiveConfig(boolean enabled, float yaw, float pitch) {
        this.enabled = enabled;
        this.yaw = yaw;
        this.pitch = pitch;
    }
}