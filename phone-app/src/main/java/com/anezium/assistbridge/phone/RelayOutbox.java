package com.anezium.assistbridge.phone;

import com.anezium.assistbridge.protocol.AssistBridgeProtocol;

import org.json.JSONObject;

final class RelayOutbox {
    interface Sender {
        boolean send(JSONObject json);
    }

    private JSONObject pendingHudSettings;
    private JSONObject pendingCommand;
    private JSONObject pendingAssistantMessage;

    synchronized void queue(JSONObject json) {
        if (json == null) {
            return;
        }
        String type = json.optString(AssistBridgeProtocol.FIELD_TYPE);
        if (AssistBridgeProtocol.TYPE_HUD_SETTINGS.equals(type)) {
            pendingHudSettings = json;
        } else if (AssistBridgeProtocol.TYPE_ASSISTANT_TEXT.equals(type)) {
            pendingAssistantMessage = json;
        } else {
            pendingCommand = json;
        }
    }

    synchronized void flush(Sender sender) {
        if (!sendPendingHudSettings(sender)) {
            return;
        }
        if (!sendPendingCommand(sender)) {
            return;
        }
        sendPendingAssistantMessage(sender);
    }

    private boolean sendPendingHudSettings(Sender sender) {
        if (pendingHudSettings == null) {
            return true;
        }
        if (!sender.send(pendingHudSettings)) {
            return false;
        }
        pendingHudSettings = null;
        return true;
    }

    private boolean sendPendingCommand(Sender sender) {
        if (pendingCommand == null) {
            return true;
        }
        if (!sender.send(pendingCommand)) {
            return false;
        }
        pendingCommand = null;
        return true;
    }

    private boolean sendPendingAssistantMessage(Sender sender) {
        if (pendingAssistantMessage == null) {
            return true;
        }
        if (!sender.send(pendingAssistantMessage)) {
            return false;
        }
        pendingAssistantMessage = null;
        return true;
    }
}
