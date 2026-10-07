package com.tvremote.tvremote;

public enum Key {

    UP("KEYCODE_DPAD_UP"), DOWN("KEYCODE_DPAD_DOWN"),
    LEFT("KEYCODE_DPAD_LEFT"), RIGHT("KEYCODE_DPAD_RIGHT"),
    OK("KEYCODE_DPAD_CENTER"), BACK("KEYCODE_BACK"),
    HOME("KEYCODE_HOME"), MUTE("KEYCODE_VOLUME_MUTE"),
    DEL("KEYCODE_DEL"), ENTER("KEYCODE_ENTER");

    final String code;
    Key(String code) { this.code = code; }
}
