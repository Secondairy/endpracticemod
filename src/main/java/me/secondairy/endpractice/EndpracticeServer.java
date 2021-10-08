package me.secondairy.endpractice;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.SERVER)
public class EndpracticeServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        Endpractice.onInitialize();

        Endpractice.commonConfigHandler();
    }
}
