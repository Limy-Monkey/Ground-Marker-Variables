package com.groundmarkervariables;

import com.groundmarkervariables.markers.ExternalMarkerManager;
import com.groundmarkervariables.markers.GroundMarkerPointData;
import com.groundmarkervariables.minify.MarkerMinifier;
import com.groundmarkervariables.variables.LabelResolver;

import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;

// Lets another plugin resolve Ground Marker Variables label text through us over RuneLite's
// own PluginMessage event, without needing to depend on or duplicate our own variable set.
//
// Request: post a PluginMessage with namespace=NAMESPACE, name=RESOLVE_EVENT, and data
// {"plugin": <your own namespace, to reply to>, "text": <the literal to resolve, e.g.
// "{rsn} is {lvl_agility}">}.
//
// Response: we post a PluginMessage back with namespace=<your namespace>, name=RESOLVE_EVENT,
// and data {"text": <your original literal, echoed back so you can match concurrent requests
// to their replies>, "result": <the resolved text>}.
//
// Resolution only ever runs LabelResolver -- MetaVariableResolver (the {tileCol=...},
// {highlight...}, {align=...} family) is deliberately NOT run, since those configure a ground
// marker tile's own rendering and don't mean anything for arbitrary text.
//
// Every token resolves as Rich Text by default (same as a marker's own label, per the user's
// own "Rich Text by default" setting) unless you prefix it yourself: "{^...}" forces Plain
// Text (e.g. "{^kc cg}" -> "385"), "{&...}" forces Rich Text (e.g. "{&kc cg}" -> "Chambers of
// Xeric kc: 385"). Prefix explicitly if you need deterministic output regardless of the
// user's own config.
//
// Some variables depend on another plugin being installed and enabled, and resolve
// differently -- or not at all, leaving the literal token in "result" unchanged -- if that
// plugin isn't running: {xpRate_<skill>} needs XP Tracker (otherwise "N/A"), {slayerTask}
// needs Slayer, {loot <monster>} needs the core Loot Tracker plugin. Don't assume a token
// resolves successfully just because its syntax is valid.
//
//  EXAMPLE USAGE SCRIPT:
//  subscribe(net.runelite.client.events.PluginMessage.class, ev -> {
//	  if ("test-script".equals(ev.getNamespace()) && "Resolve".equals(ev.getName())) {
//      log.info("Resolve reply: {}", ev.getData());
//    }
//  });
//  eventBus.post(new net.runelite.client.events.PluginMessage(
//          "ground-marker-variables",
//          "Resolve",
//          java.util.Map.of("plugin", "test-script", "text", "{^rsn} kc: {^kc toa}")
//  ));
//
// Markers: other plugins can render tiles through us, in memory only (never saved), drawn and culled like saved markers.
//
// SetMarkers, data {"plugin": <your namespace>, "group": <group name>, "priority": <number, default 0.5>,
// "markers": <JSON array as exported, e.g. [{"regionId":12850,"regionX":10,"regionY":20,"z":0,"color":"#FFFF0000",
// "label":"{hp}"}]>} replaces the whole group (at most 2000 markers). Any plugin may overwrite or clear any group.
// ClearMarkers, data {"plugin": <your namespace>, "group": <group name>} removes it. Clear your groups in shutDown().
//
// Per tile one marker is drawn: the highest priority group (ties: the latest set). A saved marker counts as 0.6, so a
// group above 0.6 is drawn instead of it, and one at or below isn't drawn where one exists. Users can hide a group's
// tile or the whole group from Shift right-click > Tile Variables, until the group is cleared.
//
//  eventBus.post(new net.runelite.client.events.PluginMessage("ground-marker-variables", "SetMarkers", java.util.Map.of(
//          "plugin", "test-script", "group", "test", "priority", 0.7,
//          "markers", "[{\"regionId\":12850,\"regionX\":10,\"regionY\":20,\"z\":0,\"color\":\"#FFFF0000\",\"label\":\"{hp}\"}]")));
@Slf4j
@Singleton
public class PluginMessageHandler
{
	static final String NAMESPACE = "ground-marker-variables";
	static final String RESOLVE_EVENT = "Resolve";
	static final String SET_MARKERS_EVENT = "SetMarkers";
	static final String CLEAR_MARKERS_EVENT = "ClearMarkers";

	private final EventBus eventBus;
	private final LabelResolver labelResolver;
	private final ClientThread clientThread;
	private final MarkerMinifier markerMinifier;
	private final ExternalMarkerManager externalMarkerManager;

	@Inject
	private PluginMessageHandler(EventBus eventBus, LabelResolver labelResolver, ClientThread clientThread, MarkerMinifier markerMinifier,
		ExternalMarkerManager externalMarkerManager)
	{
		this.eventBus = eventBus;
		this.labelResolver = labelResolver;
		this.clientThread = clientThread;
		this.markerMinifier = markerMinifier;
		this.externalMarkerManager = externalMarkerManager;
	}

	public void startUp()
	{
		eventBus.register(this);
	}

	public void shutDown()
	{
		eventBus.unregister(this);
	}

	@Subscribe
	public void onPluginMessage(PluginMessage event)
	{
		if (!NAMESPACE.equals(event.getNamespace()))
		{
			return;
		}
		if (SET_MARKERS_EVENT.equals(event.getName()) || CLEAR_MARKERS_EVENT.equals(event.getName()))
		{
			onMarkers(event.getName(), event.getData());
			return;
		}
		if (!RESOLVE_EVENT.equals(event.getName()))
		{
			return;
		}

		Object requester = event.getData().get("plugin");
		Object text = event.getData().get("text");
		if (!(requester instanceof String) || !(text instanceof String))
		{
			return;
		}

		String resolved = labelResolver.resolve((String) text);
		eventBus.post(new PluginMessage((String) requester, RESOLVE_EVENT, Map.of("text", text, "result", resolved)));
	}

	// Parsed here, on the sender's thread; applied on the client thread.
	private void onMarkers(String name, Map<String, Object> data)
	{
		Object plugin = data.get("plugin");
		Object group = data.get("group");
		if (!(plugin instanceof String) || !(group instanceof String))
		{
			log.warn("Ignoring {}: \"plugin\" and \"group\" must be strings", name);
			return;
		}

		if (CLEAR_MARKERS_EVENT.equals(name))
		{
			clientThread.invoke(() -> externalMarkerManager.clear((String) group));
			return;
		}

		Object priority = data.get("priority");
		Object markers = data.get("markers");
		if (!(markers instanceof String) || (priority != null && !(priority instanceof Number)))
		{
			log.warn("Ignoring SetMarkers from {}: \"markers\" must be a JSON string and \"priority\" a number", plugin);
			return;
		}

		List<GroundMarkerPointData> points;
		try
		{
			points = markerMinifier.parse((String) markers);
		}
		catch (Exception e)
		{
			log.warn("Ignoring SetMarkers from {}: unreadable markers", plugin, e);
			return;
		}
		if (points.size() > ExternalMarkerManager.MAX_MARKERS)
		{
			log.warn("SetMarkers from {}: only the first {} of {} markers are used", plugin, ExternalMarkerManager.MAX_MARKERS, points.size());
			points = points.subList(0, ExternalMarkerManager.MAX_MARKERS);
		}

		float value = priority == null ? ExternalMarkerManager.DEFAULT_PRIORITY : ((Number) priority).floatValue();
		List<GroundMarkerPointData> parsed = points;
		clientThread.invoke(() -> externalMarkerManager.set((String) plugin, (String) group, value, parsed));
	}
}
