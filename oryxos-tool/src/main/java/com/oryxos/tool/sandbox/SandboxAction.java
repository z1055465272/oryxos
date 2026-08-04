package com.oryxos.tool.sandbox;

/** Describes an action that must pass sandbox validation before execution. */
public record SandboxAction(ActionType type, String target) {}
