package com.groundmarkervariables;

import com.groundmarkervariables.variables.LabelResolver;

import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
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
@Singleton
public class PluginMessageHandler
{
	static final String NAMESPACE = "ground-marker-variables";
	static final String RESOLVE_EVENT = "Resolve";

	private final EventBus eventBus;
	private final LabelResolver labelResolver;

	@Inject
	private PluginMessageHandler(EventBus eventBus, LabelResolver labelResolver)
	{
		this.eventBus = eventBus;
		this.labelResolver = labelResolver;
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
		if (!NAMESPACE.equals(event.getNamespace()) || !RESOLVE_EVENT.equals(event.getName()))
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
}
