package com.eliteessentials.commands.base;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;

/**
 * Bridges the pre-0.6.0 {@code canGeneratePermission()} hook onto the current
 * {@code requireNoPermission()} API.
 */
final class CommandPermissionCompat {

    private CommandPermissionCompat() {
    }

    /**
     * Opts a command out of the engine-generated permission node.
     *
     * <p>Skipped when the command asked for generation, when an explicit permission was
     * already set with {@code requirePermission(...)}, or when registration already
     * completed (the engine rejects permission changes at that point).
     */
    static void applyNoPermission(AbstractCommand command, boolean canGeneratePermission) {
        if (canGeneratePermission) {
            return;
        }
        if (command.getPermission() != null || command.hasBeenRegistered()) {
            return;
        }
        command.requireNoPermission();
    }

    /**
     * Applies a usage variant's opt-out before it is attached to its parent.
     *
     * <p>Hytale 0.6 marks a variant as registered inside {@code addUsageVariant(...)}
     * ({@code hasBeenRegistered = true}), long before {@code setOwner(...)} runs. The
     * opt-out in {@code setOwner} is therefore skipped for variants, and the engine then
     * generates the parent's node for them (for example
     * {@code com.eliteessentials.eliteessentials.command.home} for {@code /home <name>}).
     * Opting out here, while the variant still accepts permission changes, keeps the
     * variant as open as its parent.
     */
    static void applyNoPermissionToVariant(AbstractCommand variant) {
        boolean canGeneratePermission = true;
        if (variant instanceof ElitePlayerCommand playerCommand) {
            canGeneratePermission = playerCommand.canGeneratePermission();
        } else if (variant instanceof EliteCommandBase commandBase) {
            canGeneratePermission = commandBase.canGeneratePermission();
        }
        applyNoPermission(variant, canGeneratePermission);
    }
}
