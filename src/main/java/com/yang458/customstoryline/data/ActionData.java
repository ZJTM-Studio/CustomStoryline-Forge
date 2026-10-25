package com.yang458.customstoryline.data;

import java.util.List;

public class ActionData {
    public String effect;
    public Integer effect_duration;
    public Integer effect_amplifier;

    public String text;
    public String title;
    public String subtitle;
    public String actionbar;
    public String delay_trigger;

    public Boolean set_block_player_operation;
    public Boolean set_block_touch;
    public Boolean set_block_mob_target;
    public Boolean set_block_perspective;

    public boolean unlock_block = false;
    public List<Float> set_player_perspective;
}