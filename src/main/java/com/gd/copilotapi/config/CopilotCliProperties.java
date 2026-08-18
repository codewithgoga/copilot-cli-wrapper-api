package com.gd.copilotapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "copilot.cli")
public class CopilotCliProperties {

    private String command = "gh";
    private String subcommand = "copilot";
    private String defaultModel = "auto";
    private boolean disableBuiltinMcps = true;
    private boolean noCustomInstructions = true;
    private boolean noAskUser = true;

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getSubcommand() {
        return subcommand;
    }

    public void setSubcommand(String subcommand) {
        this.subcommand = subcommand;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public boolean isDisableBuiltinMcps() {
        return disableBuiltinMcps;
    }

    public void setDisableBuiltinMcps(boolean disableBuiltinMcps) {
        this.disableBuiltinMcps = disableBuiltinMcps;
    }

    public boolean isNoCustomInstructions() {
        return noCustomInstructions;
    }

    public void setNoCustomInstructions(boolean noCustomInstructions) {
        this.noCustomInstructions = noCustomInstructions;
    }

    public boolean isNoAskUser() {
        return noAskUser;
    }

    public void setNoAskUser(boolean noAskUser) {
        this.noAskUser = noAskUser;
    }
}
