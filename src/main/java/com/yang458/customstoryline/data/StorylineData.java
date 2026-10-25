package com.yang458.customstoryline.data;

import java.util.ArrayList;
import java.util.List;

public class StorylineData {
    public List<Double> trigger_position = new ArrayList<>();
    public List<ActionData> action = new ArrayList<>();
    public ConfigData config = new ConfigData();

    public transient boolean globalTriggered = false;
}