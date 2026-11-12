package com.universalsmapi.core.detect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Device and OS facts relevant to running a 64-bit .NET game. */
public final class DeviceFacts {
    public final int sdkInt;
    public final String androidRelease;
    public final String manufacturer;
    public final String model;
    public final List<String> supportedAbis;
    public final List<String> supported64BitAbis;
    public final List<String> supported32BitAbis;

    public DeviceFacts(int sdkInt, String androidRelease, String manufacturer, String model, List<String> supportedAbis,
                       List<String> supported64BitAbis, List<String> supported32BitAbis) {
        this.sdkInt = sdkInt;
        this.androidRelease = androidRelease;
        this.manufacturer = manufacturer;
        this.model = model;
        this.supportedAbis = Collections.unmodifiableList(new ArrayList<>(supportedAbis));
        this.supported64BitAbis = Collections.unmodifiableList(new ArrayList<>(supported64BitAbis));
        this.supported32BitAbis = Collections.unmodifiableList(new ArrayList<>(supported32BitAbis));
    }

    public boolean has64Bit() {
        return !supported64BitAbis.isEmpty();
    }
}
