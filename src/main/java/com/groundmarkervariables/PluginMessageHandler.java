package com.groundmarkervariables;

import com.groundmarkervariables.chatbox.AdvancedLabelEditor;
import com.groundmarkervariables.chatbox.ConfirmChatbox;
import com.groundmarkervariables.chatbox.MarkerManager;
import com.groundmarkervariables.chatbox.SearchableChatbox;
import com.groundmarkervariables.markers.ExternalMarkerManager;
import com.groundmarkervariables.markers.GroundMarkerPointData;
import com.groundmarkervariables.markers.minify.MarkerMinifier;
import com.groundmarkervariables.variables.LabelResolver;

import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxInput;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.util.Text;

// Ground Marker Variables PluginMessage API.
//
// Other plugins talk to us through RuneLite's PluginMessage event: post one to the EventBus with
// namespace "ground-marker-variables", a message name below, and its data. Unknown names and malformed data are
// ignored (with a log warning for markers). There are three APIs:
//
//   1. Resolve      -- resolve label text containing our variables, e.g. "{rsn} is {lvl_agility}".
//   2. SetMarkers / ClearMarkers -- render tiles through our engine.
//   3. Search       -- ask to search the Marker Manager, e.g. for a tile.
//
// Replies: put an empty, mutable Map<String, Object> (e.g. a HashMap) in the data under "reply". EventBus.post() is
// synchronous, so we fill it in before post() returns. If it's still empty afterwards, Ground Marker Variables isn't
// running. Every reply includes "version" (Integer, currently 1), raised on breaking changes.
//
// ---------------------------------------------------------------------------------------------------------------
// 1. Resolve
// ---------------------------------------------------------------------------------------------------------------
// Request   name "Resolve"
//   text    String  required  Text to resolve.
//   reply   Map     required  Filled in with the result.
//
// Reply
//   result   String           The resolved text.
//   version  Integer          API version.
//
// Notes
//   - Only variables resolve. Tile settings ({tileCol}, {highlight}, {align}, {ground}, ...) are left as written.
//   - Tokens resolve as Rich Text by default (the user's "Rich Text by default" setting). Prefix "{^...}" for Plain
//     Text ("{^kc cg}" -> "385") or "{&...}" for Rich Text, for output independent of the user's config.
//   - A token that can't resolve is left as written. Some need another plugin running: {xpRate_<skill>} needs XP
//     Tracker ("N/A" otherwise), {loot <monster>} the Loot Tracker.
//
// Example
//   Map<String, Object> reply = new HashMap<>();
//   eventBus.post(new PluginMessage("ground-marker-variables", "Resolve",
//       Map.of("text", "{^rsn} kc: {^kc toa}", "reply", reply)));
//   String result = (String) reply.get("result"); // null if Ground Marker Variables isn't running
//
// ---------------------------------------------------------------------------------------------------------------
// 2. SetMarkers / ClearMarkers
// ---------------------------------------------------------------------------------------------------------------
// Request   name "SetMarkers": replaces every marker in the group.
//   plugin    String  required  Your namespace, shown to users as the group's owner.
//   group     String  required  Group name (case-sensitive). Any plugin may set or clear any group.
//   priority  Number  optional  Default 0.5. See "Drawing" below.
//   markers   String  required  JSON array, as our exports (gmv-compressed labels work too), at most 2000:
//                                [{"regionId":12850,"regionX":10,"regionY":20,"z":0,"color":"#FFFF0000","label":"{rsn}"}]
//   reply     Map     optional  Filled in with the outcome.
//
// Request   name "ClearMarkers": removes the group.
//   plugin    String  required  Your namespace.
//   group     String  required  Group name.
//   reply     Map     optional  Filled in with the outcome.
//
// Reply
//   ok       Boolean          Whether the request was accepted.
//   count    Integer          SetMarkers only: markers accepted (after the 2000 cap).
//   error    String           Why it was rejected or capped, if it was.
//   version  Integer          API version.
//
// Drawing
//   - Markers live in memory only (never saved).
//   - Markers are cleared on shutDown() or on ClearMarkers API below.
//   - One marker is drawn per tile: the highest priority, ties going to the latest set. Core saved markers count as 0.6,
//     so a group above 0.6 is drawn over a core marker and one at or below 0.6 gives way to it. Party pings count
//     as 0.55; import previews draw over everything.
//   - Users have the option to hide these tiles (Shift right-click > Tile Variables). This lasts until the group is cleared.
//
// Examples
//   Map<String, Object> reply = new HashMap<>();
//   eventBus.post(new PluginMessage("ground-marker-variables", "SetMarkers", Map.of(
//       "plugin", "my-plugin", "group", "my-plugin-tiles", "priority", 0.7, "reply", reply,
//       "markers", "[{\"regionId\":12850,\"regionX\":10,\"regionY\":20,\"z\":0,\"color\":\"#FFFF0000\",\"label\":\"{rsn}\"}]")));
//   if (reply.isEmpty()) { /* Ground Marker Variables isn't running */ }
//   else if (!(Boolean) reply.get("ok")) { log.warn("Markers rejected: {}", reply.get("error")); }
//
//   eventBus.post(new PluginMessage("ground-marker-variables", "ClearMarkers",
//       Map.of("plugin", "my-plugin", "group", "my-plugin-tiles")));
//
// ---------------------------------------------------------------------------------------------------------------
// 3. Search
// ---------------------------------------------------------------------------------------------------------------
// Request   name "Search"
//   plugin  String  required  Your plugin's name, shown to the user.
//   query   String  required  The Marker Manager search, e.g. "@3029,3217,0" for a tile (world x, y, plane), which
//                             finds it in saved markers and every marker set.
//   reply   Map     optional  Filled in with the outcome.
//
// Reply
//   ok       Boolean          Whether the user was asked (the search runs only if they accept).
//   error    String           Why not, if not.
//   version  Integer          API version.
//
// Notes
//   - Only while the user has the Marker Manager, Marker Sets or the label editor open; otherwise nothing happens.
//     They're asked "<plugin> is requesting to link a marker" (Open Link / Cancel, back to where they were).
//
// Example
//   eventBus.post(new PluginMessage("ground-marker-variables", "Search",
//       Map.of("plugin", "My Plugin", "query", "@3029,3217,0")));
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PluginMessageHandler {
	static final String NAMESPACE = "ground-marker-variables";
	static final String RESOLVE_EVENT = "Resolve";
	static final String SET_MARKERS_EVENT = "SetMarkers";
	static final String CLEAR_MARKERS_EVENT = "ClearMarkers";
	static final String SEARCH_EVENT = "Search";
	// Raised on breaking changes to the API above.
	static final int API_VERSION = 1;

	private final EventBus eventBus;
	private final LabelResolver labelResolver;
	private final ClientThread clientThread;
	private final MarkerMinifier markerMinifier;
	private final ExternalMarkerManager externalMarkerManager;
	private final ChatboxPanelManager chatboxPanelManager;
	private final Provider<MarkerManager> markerManagerProvider;

	public void startUp() {
		eventBus.register(this);
	}

	public void shutDown() {
		eventBus.unregister(this);
	}

	@Subscribe
	public void onPluginMessage(PluginMessage event) {
		if (!NAMESPACE.equals(event.getNamespace())) {
			return;
		}
		if (SET_MARKERS_EVENT.equals(event.getName()) || CLEAR_MARKERS_EVENT.equals(event.getName())) {
			onMarkers(event.getName(), event.getData());
			return;
		}
		if (SEARCH_EVENT.equals(event.getName())) {
			onSearch(event.getData());
			return;
		}
		if (!RESOLVE_EVENT.equals(event.getName())) {
			return;
		}

		Object text = event.getData().get("text");
		Map<String, Object> reply = reply(event.getData());
		if (!(text instanceof String) || reply == null) {
			return;
		}

		fill(reply, Map.of("result", labelResolver.resolve((String) text)));
	}

	// Parsed and answered here, on the sender's thread; applied on the client thread.
	private void onMarkers(String name, Map<String, Object> data) {
		Map<String, Object> reply = reply(data);
		Object plugin = data.get("plugin");
		Object group = data.get("group");
		if (!(plugin instanceof String) || !(group instanceof String)) {
			reject(reply, name, "\"plugin\" and \"group\" must be strings", null);
			return;
		}

		if (CLEAR_MARKERS_EVENT.equals(name)) {
			clientThread.invoke(() -> externalMarkerManager.clear((String) group));
			fill(reply, Map.of("ok", true));
			return;
		}

		Object priority = data.get("priority");
		Object markers = data.get("markers");
		if (!(markers instanceof String) || (priority != null && !(priority instanceof Number))) {
			reject(reply, name, "\"markers\" must be a JSON string and \"priority\" a number", null);
			return;
		}

		List<GroundMarkerPointData> points;
		try {
			points = markerMinifier.parse((String) markers);
		} catch (Exception e) {
			reject(reply, name, "unreadable markers JSON", e);
			return;
		}

		String error = null;
		if (points.size() > ExternalMarkerManager.MAX_MARKERS) {
			error = "only the first " + ExternalMarkerManager.MAX_MARKERS + " of " + points.size() + " markers are used";
			log.warn("SetMarkers from {}: {}", plugin, error);
			points = points.subList(0, ExternalMarkerManager.MAX_MARKERS);
		}

		float value = priority == null ? ExternalMarkerManager.DEFAULT_PRIORITY : ((Number) priority).floatValue();
		List<GroundMarkerPointData> parsed = points;
		clientThread.invoke(() -> externalMarkerManager.set((String) plugin, (String) group, value, parsed));
		fill(reply, error == null ? Map.of("ok", true, "count", parsed.size()) : Map.of("ok", true, "count", parsed.size(), "error", error));
	}

	// Only while the Marker Manager, Marker Sets or the label editor is open: asks to open the search (Marker Manager),
	// else goes back to it as it was.
	private void onSearch(Map<String, Object> data) {
		Map<String, Object> reply = reply(data);
		Object plugin = data.get("plugin");
		Object query = data.get("query");
		if (!(plugin instanceof String) || !(query instanceof String)) {
			reject(reply, SEARCH_EVENT, "\"plugin\" and \"query\" must be strings", null);
			return;
		}
		ChatboxInput open = chatboxPanelManager.getCurrentInput();
		AdvancedLabelEditor editor = open instanceof AdvancedLabelEditor ? (AdvancedLabelEditor) open : null;
		if (!(open instanceof SearchableChatbox) && editor == null) {
			fill(reply, Map.of("ok", false, "error", "no Ground Marker Variables chatbox is open"));
			return;
		}
		clientThread.invoke(() -> {
			// The editor comes back as it was; leaving it for the search closes it as usual (e.g. its preview).
			if (editor != null) {
				editor.handOff();
			}
			new ConfirmChatbox(chatboxPanelManager, Text.escapeJagex((String) plugin) + " is requesting to link a marker")
				.option("Open Link", () -> {
					if (editor != null) {
						chatboxPanelManager.openInput(editor);
					}
					markerManagerProvider.get().show(null, ((String) query).trim());
				})
				.option("Cancel", () -> { })
				// Cancel or Esc: back to where they were.
				.onClose(() -> clientThread.invokeLater(() -> {
					if (chatboxPanelManager.getCurrentInput() == null) {
						chatboxPanelManager.openInput(open);
					}
				}))
				.build();
		});
		fill(reply, Map.of("ok", true));
	}

	private static void reject(Map<String, Object> reply, String name, String error, Exception e) {
		log.warn("Ignoring {}: {}", name, error, e);
		fill(reply, Map.of("ok", false, "error", error));
	}

	// The caller's mutable map under "reply", or null.
	@SuppressWarnings("unchecked")
	private static Map<String, Object> reply(Map<String, Object> data) {
		Object reply = data.get("reply");
		return reply instanceof Map ? (Map<String, Object>) reply : null;
	}

	// Fills the caller's reply, if they passed one, with values and the API version.
	private static void fill(Map<String, Object> reply, Map<String, Object> values) {
		if (reply == null) {
			return;
		}
		try {
			reply.putAll(values);
			reply.put("version", API_VERSION);
		} catch (UnsupportedOperationException | ClassCastException e) {
			log.warn("Couldn't fill a PluginMessage reply: it must be a mutable Map<String, Object>", e);
		}
	}
}
