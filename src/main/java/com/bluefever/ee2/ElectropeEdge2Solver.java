package com.bluefever.ee2;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.reevent.scan.ScanMe;
import gg.xp.xivsupport.events.actlines.events.*;
import gg.xp.xivsupport.events.actlines.events.actorcontrol.DutyCommenceEvent;
import gg.xp.xivsupport.events.debug.DebugCommand;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.marks.ClearAutoMarkRequest;
import gg.xp.xivsupport.events.triggers.marks.adv.MarkerSign;
import gg.xp.xivsupport.events.triggers.marks.adv.SpecificAutoMarkRequest;
import gg.xp.xivsupport.gui.TitleBorderFullsizePanel;
import gg.xp.xivsupport.gui.extra.PluginTab;
import gg.xp.xivsupport.models.XivCombatant;
import gg.xp.xivsupport.models.XivPlayerCharacter;
import gg.xp.xivsupport.models.XivEntity;
import gg.xp.xivsupport.persistence.PersistenceProvider;
import gg.xp.xivsupport.persistence.settings.BooleanSetting;
import gg.xp.xivsupport.persistence.settings.LongSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Component;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@ScanMe
public class ElectropeEdge2Solver implements PluginTab {
	private static final Logger log = LoggerFactory.getLogger(ElectropeEdge2Solver.class);

	private final Map<XivPlayerCharacter, Integer> longDebuffPlayers = new HashMap<>();
	private final Map<XivPlayerCharacter, Integer> shortDebuffPlayers = new HashMap<>();
	private final BooleanSetting enableAutomark;
	private final LongSetting markerClearDelay;

	private static final int condenserBuffId = 0xF9F;
	private static final int witchGleamAbilityId = 0x9786;
	private static final int lightningCageAbilityId = 0x95CE;

	private static final int longDebuffTimer = 30;
	private static final long timeTillNextGroupMs = 17000L; // milliseconds

    public ElectropeEdge2Solver(PersistenceProvider persistence) {
		enableAutomark = new BooleanSetting(persistence, "ee2-solver.automark.enable", true);
		markerClearDelay = new LongSetting(persistence, "ee2-solver.clear-delay", 10000L);
    }

	@Override
	public String getTabName() {
		return "EE2";
	}

	@Override
	public Component getTabContents() {
        return new TitleBorderFullsizePanel("EE2 Plugin");
    }

	@HandleEvents
	public void amTest(EventContext context, DebugCommand event) {
		XivState xivState = context.getStateInfo().get(XivState.class);
		List<XivPlayerCharacter> partyList = xivState.getPartyList();
		if (event.getCommand().equals("ee2test")) {
			List<XivPlayerCharacter> supports = new ArrayList<>(partyList
                    .stream()
                    .filter(p -> p.getJob().isSupport())
                    .toList());

			List<XivPlayerCharacter> dps = new ArrayList<>(partyList
					.stream()
					.filter(p -> p.getJob().isDps())
					.toList());

			Map<XivPlayerCharacter, Integer> shortDebuffs = new HashMap<>();
			Map<XivPlayerCharacter, Integer> longDebuffs = new HashMap<>();

			Random rand = new Random();

			List<Integer> shortOptions = Arrays.asList(2,3);
			List<Integer> longOptions = Arrays.asList(2,3);

			for (Integer option: shortOptions) {
				if (!supports.isEmpty()) {
					int randomSupportIndex = rand.nextInt(supports.size());
					XivPlayerCharacter support = supports.remove(randomSupportIndex);
					shortDebuffs.put(support, option);
				}

				if (!dps.isEmpty()) {
					int randomDpsIndex = rand.nextInt(dps.size());
					XivPlayerCharacter dpsChar = dps.remove(randomDpsIndex);
					shortDebuffs.put(dpsChar, option);
				}
			}

			for (Integer option: longOptions) {
				if (!supports.isEmpty()) {
					int randomSupportIndex = rand.nextInt(supports.size());
					XivPlayerCharacter support = supports.remove(randomSupportIndex);
					longDebuffs.put(support, option);
				}

				if (!dps.isEmpty()) {
					int randomDpsIndex = rand.nextInt(dps.size());
					XivPlayerCharacter dpsChar = dps.remove(randomDpsIndex);
					longDebuffs.put(dpsChar, option);
				}
			}


			log.info("Short Debuffs: {}", shortDebuffs.keySet().stream().map(XivEntity::getName).collect(Collectors.joining(", ")));
			log.info("Long Debuffs: {}", longDebuffs.keySet().stream().map(XivEntity::getName).collect(Collectors.joining(", ")));

			context.accept(new WitchGleamCountSolvedEvent(shortDebuffs));

			WitchGleamCountSolvedEvent nextSet = new WitchGleamCountSolvedEvent(longDebuffs);
			nextSet.setDelayedEnqueueOffset(Duration.ofMillis(timeTillNextGroupMs));
			context.enqueue(nextSet);
		}
	}

	@HandleEvents
	public void handleWipe(EventContext context, DutyCommenceEvent event) {
		clearPlayers();
	}

	@HandleEvents
	public void handleWipe(EventContext context, WipeEvent event) {
		clearPlayers();
	}

	@HandleEvents
	public void handleWipe(EventContext context, ZoneChangeEvent event) {
		clearPlayers();
	}

	@HandleEvents
	public void handleCondenserBuff(EventContext context, BuffApplied event) {
		long buffId = event.getBuff().getId();
		if (buffId != condenserBuffId) {
			return;
		}

		XivCombatant target = event.getTarget();
		XivPlayerCharacter playerCharacter = null;
		if (target instanceof XivPlayerCharacter pc) {
			playerCharacter = pc;
		}

		if (playerCharacter != null) {
			if (event.getInitialDuration().getSeconds() > longDebuffTimer) {
				log.info("Adding: {} to long debuff list", playerCharacter.getName());
				longDebuffPlayers.put(playerCharacter, 1);
			} else {
				log.info("Adding: {} to short debuff list", playerCharacter.getName());
				shortDebuffPlayers.put(playerCharacter, 0);
			}
		}
	}

	@HandleEvents
	public void handleLightningCage(EventContext context, AbilityCastStart event) {
		long abilityId = event.getAbility().getId();
		if (abilityId != lightningCageAbilityId) {
			return;
		}

		// lightning cage indicates the first group of people need to be marked
        context.accept(new WitchGleamCountSolvedEvent(shortDebuffPlayers));

        WitchGleamCountSolvedEvent nextSet = new WitchGleamCountSolvedEvent(longDebuffPlayers);
        nextSet.setDelayedEnqueueOffset(Duration.ofMillis(timeTillNextGroupMs));
        context.enqueue(nextSet);
    }


	@HandleEvents
	public void handleWitchGleam(EventContext context, AbilityUsedEvent event) {
		long abilityId = event.getAbility().getId();
		if (abilityId != witchGleamAbilityId) {
			return;
		}

		XivCombatant target = event.getTarget();

        if (target instanceof XivPlayerCharacter pc) {
            if (longDebuffPlayers.containsKey(pc)) {
                longDebuffPlayers.merge(pc, 1, Integer::sum);
            } else {
                shortDebuffPlayers.merge(pc, 1, Integer::sum);
            }
        }
    }

	private void clearPlayers() {
		log.info("Cleared players");
		shortDebuffPlayers.clear();
		longDebuffPlayers.clear();
	}

	@HandleEvents(order = 1)
	public void automarks(EventContext context, WitchGleamCountSolvedEvent event) {
		if (enableAutomark.get()) {
			Map<XivPlayerCharacter, Integer> playersToMark = event.getWitchGleamPlayers();
			log.info("Requesting to mark witch gleam group players: {}", playersToMark.keySet().stream().map(XivEntity::getName).collect(Collectors.joining(", ")));

			for (var entry : playersToMark.entrySet()) {
				XivPlayerCharacter player = entry.getKey();
				Integer hitCount = entry.getValue();

				if (player.getJob().isSupport()) {
					switch (hitCount) {
						case 2 -> context.accept(new SpecificAutoMarkRequest(player, MarkerSign.BIND2));
						case 3 -> context.accept(new SpecificAutoMarkRequest(player, MarkerSign.BIND3));
					}
				} else if (player.getJob().isDps()) {
					switch (hitCount) {
						case 2 -> context.accept(new SpecificAutoMarkRequest(player, MarkerSign.ATTACK2));
						case 3 -> context.accept(new SpecificAutoMarkRequest(player, MarkerSign.ATTACK3));
					}
				}
			}

			ClearAutoMarkRequest clear = new ClearAutoMarkRequest();
			clear.setDelayedEnqueueOffset(markerClearDelay.get());
			context.enqueue(clear);
		}
		else {
			log.info("Automarkers disabled, skipping");
		}
	}

	public BooleanSetting getEnableAutomark() {
		return enableAutomark;
	}
}
