package com.bar.foo;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.xivsupport.models.XivPlayerCharacter;

import java.io.Serial;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class WitchGleamCountSolvedEvent extends BaseEvent {

    @Serial
    private static final long serialVersionUID = -358330710284359399L;
    private final Map<XivPlayerCharacter, Integer> groupPlayers;

    public WitchGleamCountSolvedEvent(Map<XivPlayerCharacter, Integer> groupPlayers) {
        this.groupPlayers = new HashMap<>(groupPlayers);
    }

    public Map<XivPlayerCharacter, Integer> getWitchGleamPlayers() {
        return Collections.unmodifiableMap(groupPlayers);
    }
}