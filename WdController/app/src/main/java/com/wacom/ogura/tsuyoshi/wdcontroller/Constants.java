package com.wacom.ogura.tsuyoshi.wdcontroller;

import java.util.UUID;

public interface Constants {
    public static final String BT_DEVICE = "Nexus 7";
    public static final UUID BT_UUID = UUID.fromString(
//            "41eb5f39-6c3a-4067-8bb9-bad64e6e0908");
"34B1CF4D-1069-4AD6-89B6-E161D79BE4D2");    // for WdP1.1

    public static final String STATE_TEMP = "STATE_TEMP";

    public static final int MESSAGE_BT = 0;
    public static final int MESSAGE_TEMP = 2;
}
