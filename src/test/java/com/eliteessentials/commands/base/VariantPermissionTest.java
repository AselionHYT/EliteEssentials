package com.eliteessentials.commands.base;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandOwner;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Usage variants such as {@code /home <name>} must stay as open as their parent command,
 * so that the plugin's own permission check is the only one that applies.
 */
class VariantPermissionTest {

    private static final CommandOwner OWNER = () -> "EliteEssentialsTest";

    @Test
    void engineMarksVariantAsRegisteredWhenItIsAdded() {
        // The root cause: Hytale 0.6 sets hasBeenRegistered on a variant inside
        // addUsageVariant, so an opt-out in setOwner comes too late for variants.
        // If this ever fails, the engine changed and the workaround can be revisited.
        NamedVariant variant = new NamedVariant(false);
        new PlainParent(variant);

        assertTrue(variant.hasBeenRegistered());
    }

    @Test
    void optedOutVariantNeedsNoEngineNode() {
        NamedVariant variant = new NamedVariant(false);
        OpenParent parent = new OpenParent(variant);

        parent.setOwner(OWNER);

        assertNull(parent.getPermission(), "parent command must be open");
        assertNull(variant.getPermission(), "variant must not get a generated node");
    }

    @Test
    void explicitVariantPermissionIsKept() {
        NamedVariant variant = new NamedVariant(false);
        variant.requirePermission("eliteessentials.test.explicit");
        OpenParent parent = new OpenParent(variant);

        parent.setOwner(OWNER);

        assertTrue("eliteessentials.test.explicit".equals(variant.getPermission()));
    }

    private static final class OpenParent extends ElitePlayerCommand {
        OpenParent(ElitePlayerCommand variant) {
            super("testhome", "Test parent");
            addUsageVariant(variant);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        }
    }

    private static final class PlainParent extends AbstractPlayerCommand {
        PlainParent(ElitePlayerCommand variant) {
            super("plainhome", "Plain engine parent");
            addUsageVariant(variant);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        }
    }

    private static final class NamedVariant extends ElitePlayerCommand {
        private final boolean generatePermission;

        NamedVariant(boolean generatePermission) {
            super("Test variant");
            this.generatePermission = generatePermission;
            withRequiredArg("name", "Name", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return generatePermission;
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        }
    }
}
