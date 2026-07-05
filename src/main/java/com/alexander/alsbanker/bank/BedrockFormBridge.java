package com.alexander.alsbanker.bank;

import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.floodgate.api.FloodgateApi;

public class BedrockFormBridge {
    public static void sendTransferForm(Player player) {
        CustomForm form = CustomForm.builder()
                .title("AllyCraft Bank")
                .input("Target Player", "Name")
                .input("Amount", "0.00")
                .validResultHandler(res -> {
                    // Transaction logic goes here
                })
                .build();
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }
}