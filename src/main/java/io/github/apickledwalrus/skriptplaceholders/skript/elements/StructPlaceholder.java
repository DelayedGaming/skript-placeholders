package io.github.apickledwalrus.skriptplaceholders.skript.elements;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.Skript;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.parser.ParserInstance;
import ch.njol.skript.lang.util.SimpleEvent;
import ch.njol.skript.registrations.EventValues;
import ch.njol.skript.util.Getter;
import io.github.apickledwalrus.skriptplaceholders.SkriptPlaceholders;
import io.github.apickledwalrus.skriptplaceholders.placeholder.PlaceholderEvaluator;
import io.github.apickledwalrus.skriptplaceholders.skript.PlaceholderEvent;
import io.github.apickledwalrus.skriptplaceholders.placeholder.PlaceholderListener;
import io.github.apickledwalrus.skriptplaceholders.placeholder.PlaceholderPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.eclipse.jdt.annotation.Nullable;
import org.skriptlang.skript.lang.entry.EntryContainer;
import org.skriptlang.skript.lang.script.Script;
import org.skriptlang.skript.lang.structure.Structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Name("Placeholder Request")
@Description({
	"Triggers whenever the value of a placeholder is requested by a supported placeholder plugin.",
})
@Examples({
	"on placeholderapi placeholder request for the prefix \"skriptplaceholders\":",
		"\tif the identifier is \"author\": # Placeholder is \"%skriptplaceholders_author%\"",
			"\t\tset the result to \"APickledWalrus\"",
	"on mvdw placeholder request for the placeholder \"skriptplaceholders_author\":",
		"\t# Placeholder is \"{skriptplaceholders_author}\"",
		"\tset the result to \"APickledWalrus\""
})
@Since("1.0, 1.3 (MVdWPlaceholderAPI support)")
public class StructPlaceholder extends Structure implements PlaceholderEvaluator {

	static {
		Skript.registerStructure(StructPlaceholder.class,
				"[on] (placeholder[ ]api|papi) [placeholder] request (for|with) [the] prefix[es] %*strings%",
				"[on] (mvdw[ ]placeholder[ ]api|mvdw) [placeholder] request (for|with) [the] placeholder[s] %*strings%"
		);
		EventValues.registerEventValue(PlaceholderEvent.class, Player.class, new Getter<Player, PlaceholderEvent>() {
			@Override
			@Nullable
			public Player get(PlaceholderEvent event) {
				if (event.getPlayer() != null && event.getPlayer().isOnline())
					return (Player) event.getPlayer();
				return null;
			}
		}, EventValues.TIME_NOW);
		EventValues.registerEventValue(PlaceholderEvent.class, OfflinePlayer.class, new Getter<OfflinePlayer, PlaceholderEvent>() {
			@Override
			@Nullable
			public OfflinePlayer get(PlaceholderEvent event) {
				return event.getPlayer();
			}
		}, EventValues.TIME_NOW);
	}

	@SuppressWarnings("NotNullFieldNotInitialized")
	private PlaceholderPlugin plugin;
	@SuppressWarnings("NotNullFieldNotInitialized")
	private String[] placeholders;

	@SuppressWarnings("NotNullFieldNotInitialized")
	private PlaceholderListener[] listeners;
	@SuppressWarnings("NotNullFieldNotInitialized")
	private Trigger trigger;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Literal<?>[] args, int matchedPattern, ParseResult parseResult, EntryContainer entryContainer) {
		plugin = PlaceholderPlugin.values()[matchedPattern];
		if (!plugin.isInstalled()) {
			Skript.error(plugin.getDisplayName() + " placeholders can not be requested because the plugin is not installed.");
			return false;
		}

		List<String> placeholders = new ArrayList<>();
		for (String placeholder : ((Literal<String>) args[0]).getAll()) {
			String error = plugin.isValidPrefix(placeholder);
			if (error != null) {
				Skript.error(error);
				return false;
			}
			placeholders.add(placeholder);
		}

		this.placeholders = placeholders.toArray(new String[0]);
		this.listeners = new PlaceholderListener[this.placeholders.length];

		return true;
	}

	@Override
	public boolean load() {
		ParserInstance parser = getParser();
		Script script = parser.getCurrentScript();
		SectionNode source = getEntryContainer().getSource();

		parser.setCurrentEvent("placeholder request", PlaceholderEvent.class);

		// TODO better SkriptEvent?
		trigger = new Trigger(script, "placeholder request", new SimpleEvent(), ScriptLoader.loadItems(source));
		int lineNumber = source.getLine();
		trigger.setLineNumber(lineNumber);
		trigger.setDebugLabel(script + ": line " + lineNumber);

		// see https://github.com/APickledWalrus/skript-placeholders/issues/40
		// ensure registration is on the main thread
		if (Bukkit.isPrimaryThread()) {
			for (int i = 0; i < placeholders.length; i++) {
				listeners[i] = plugin.registerPlaceholder(this, placeholders[i]);
			}
		} else {
			Bukkit.getScheduler().runTask(SkriptPlaceholders.getInstance(), () -> {
				for (int i = 0; i < placeholders.length; i++) {
					listeners[i] = plugin.registerPlaceholder(this, placeholders[i]);
				}
			});
		}

		return true;
	}

	@Override
	public void unload() {
		for (PlaceholderListener listener : listeners) {
			listener.unregisterListener();
		}
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		String placeholders = Arrays.toString(this.placeholders);
		placeholders = placeholders.substring(1, placeholders.length() - 1); // Trim off the ends
		switch (plugin) {
			case PLACEHOLDER_API:
				return "placeholderapi request for the prefixes " + placeholders;
			case MVDW_PLACEHOLDER_API:
				return "mvdwplaceholderapi request for the placeholders " + placeholders;
			default:
				throw new IllegalArgumentException("Unable to handle PlaceholderPlugin: " + plugin);
		}
	}

	@Override
	@Nullable
	public String evaluate(String placeholder, @Nullable OfflinePlayer player) {
		PlaceholderEvent event = new PlaceholderEvent(placeholder, player);
		trigger.execute(event);
		return event.getResult();
	}

}
