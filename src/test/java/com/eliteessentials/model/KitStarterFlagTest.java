package com.eliteessentials.model;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which kits from kits.json are handed out automatically on first join. */
class KitStarterFlagTest {

    private final Gson gson = new Gson();

    private Kit parse(String json) {
        return gson.fromJson(json, Kit.class);
    }

    @Test
    void kitNamedStarterWithoutFlagIsAStarterKit() {
        assertTrue(parse("{\"id\":\"starter\",\"items\":[]}").isStarterKit());
        assertTrue(parse("{\"id\":\"Starter\",\"items\":[]}").isStarterKit());
    }

    @Test
    void explicitFalseOptsAKitNamedStarterOut() {
        assertFalse(parse("{\"id\":\"starter\",\"starterKit\":false,\"items\":[]}").isStarterKit());
    }

    @Test
    void explicitTrueMakesAnyKitAStarterKit() {
        assertTrue(parse("{\"id\":\"welcome\",\"starterKit\":true,\"items\":[]}").isStarterKit());
        assertFalse(parse("{\"id\":\"welcome\",\"items\":[]}").isStarterKit());
    }

    @Test
    void constructorFlagIsKept() {
        assertFalse(new Kit("starter", "Starter", "", "", 0, false, true, false, java.util.List.of()).isStarterKit());
        assertTrue(new Kit("daily", "Daily", "", "", 0, false, false, true, java.util.List.of()).isStarterKit());
    }
}
