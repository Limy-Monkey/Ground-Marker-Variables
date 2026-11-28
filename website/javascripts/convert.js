// Converts markers copied by Ground Marker Variables before 2.0, whose labels were compressed ("gmv": "1~<digits>"),
// back to plain labels the plugin imports. These are the plugin's old reader's steps (GmvAlphabet, MarkerCompression):
// the digits are one big number in ALPHABET's characters, whose bytes after a leading 1 are raw deflate primed with
// DICTIONARY. Never change either: they're what those copies were written with.
(function () {
  // First/last character pairs.
  const RANGES = "¡§©©¬¬®®°±¶·¿¿ÆÆÐÐ×ØÞßææðð÷øþþĐđĦħııĸĸŁłŊŋŒœŦŧƀƟƢƮƱǃǝǝǤǥǶǷȜȝȠȠȢȥɐʭ͵͵ΑΡΣΩαωϗϯϳϳ϶϶ЂЂЄІЈЋЏИКикяђђєіјћџѵѸ҂ҊӀӃӎӔӕӘәӠӡӨөԀԏԱՖՙ՟աֆ։֊ႠჅაჸ჻჻ᎠᏴᐁᙶᚁ᚜ᚠᛰ℄℄℈℈℔℔℗℘℞℟℣℣℥℥℧℧℩℩℮℮ℲℲ℺℺⅁⅄⅊⅋←↓↜↨↫↭↯⇌⇐∃∅∈∊∋∍∣∥∥∧∫∮∮∱≀≂≃≅≆≈≈≊≟≡≡≣≬≲≳≶≷≺≿⊂⊃⊆⊇⊊⊫⊰⋟⋤⋩⋮⌙⌜⌧⌫⏎␀␦⑀⑊─▩▬▵▷▿◁◺◿◿✁✁✃✄✆✇✎✎✐✑✓✓✕✕✗✜✞✠✢✧✩✲✵❃❅❆❈❋❍❍❏❒❖❖❘❞❡❢❥➔➘➠➢➯➱➾⟰⤳⤶⨋⨍⩳⩷⫛⫝⫿";
  // Version 1's dictionary (dictionary-1.txt, its lines joined; SHA-256 81eab1acbd9c88a475d263b11e4a851d09d61bcf1c0fafaa82fef37debfcbd44).
  const DICTIONARY = "Agility Arena Total TicketObor chests openedGalvekJubbly JiveTempor TantrumGwenith Glide{kc cox cm}{kc tob hm}{kc toa expert}{kc nightmare}{kc corp}{kc zulrah}{kc vorkath}{kc muspah}{kc nex}{kc hmt}{kc cg}{kc toa}{kc tob}{kc cox}{kc tfa}Bastion Battlemage Salts Overload Combat}{divine helm}cape}amulet}body}shield}legs}gloves}boots}ring}ammo}quiver}{equip_runecraft}construction}hunter}farming}firemaking}woodcutting}fishing}mining}smithing}cooking}crafting}fletching}thieving}herblore}agility}slayer}sailing}total}combat}prayer}hitpoints}defence}strength}attack}ranged}magic}{xpRate_{xp_{boost_{lvl_{6HourTimeRemaining < 1}{worldInfo hideWorld}{combatAchievement}{questPoints}{miscellania}{slayerStreak}{slayerPoints}{slayerTask}{time24}{time}{date}{weekday}{world}{rsn}{runEnergy}{spec}{autoRetaliate}{autocast}{attackType}{attackStyle}{weapon}{party num}{party name}{party hp}{party prayer}{highlightCorners}{highlightAll}{highlightSW=}{tileCol=}{borderWidth=}{numDoses }{numItem }{rank }{pb }{loot }, items=, lines=, itemsOnly}{hasVileVigour}{hasEntangle}{hasAlchs}{hasFreeze}{hasVengeance}{hasThralls}{metronome4_5}{metronome}{m+}{dps report }{dps groupReport }{dps bossReport }{dps fullReport }noLegend avgDps theoryDps pureTheoryDps shareDps splitDps totalDamage takenDamage thrallDps recoilDps numAttacks numHits numMisses timeSpent ticksSpent ticksUsed ticksLost (acc)}{dps }{b}{/b}{i}{/i}{align=center}{align=right}{align=left}{/align}{col=black}{col=blue}{col=purple}{col=magenta}{col=lightgray}{col=darkgray}{col=limy}{col=white}{col=gray}{col=orange}{col=cyan}{col=teal}{col=yellow}{col=green}{col=red}{/col}Empty}{equip_ammo != Rune Pouch}{hasItem {spellbook == Standard ? }{spellbook == Ancient ? }{spellbook == Lunar ? }{spellbook == Arceuus ? }{spellbook} && {hasItem  || hasItem  != } > 0 ? } < 1 ? } : } | {hasItem }{kc }\\n{";

  const digits = new Map();
  for (let i = 0; i < RANGES.length; i += 2) {
    for (let c = RANGES.charCodeAt(i); c <= RANGES.charCodeAt(i + 1); c++) {
      digits.set(String.fromCharCode(c), digits.size);
    }
  }
  const BASE = BigInt(digits.size);

  // The bytes a label's digits are; throws if they aren't any.
  function bytes(text) {
    let value = 0n;
    for (let i = 0; i < text.length; i++) {
      const digit = digits.get(text[i]);
      if (digit === undefined || (i === 0 && digit === 0)) {
        throw new Error("isn't a compressed label");
      }
      value = value * BASE + BigInt(digit);
    }
    let hex = value.toString(16);
    hex = hex.length % 2 ? "0" + hex : hex;
    const out = new Uint8Array(hex.length / 2);
    for (let i = 0; i < out.length; i++) {
      out[i] = parseInt(hex.substr(i * 2, 2), 16);
    }
    if (out[0] !== 1) {
      throw new Error("is damaged");
    }
    return out.subarray(1);
  }

  // A compressed "gmv" value's label.
  function label(gmv) {
    const match = /^(\d+)~(.+)$/s.exec(gmv);
    if (!match) {
      throw new Error("isn't a compressed label");
    }
    if (match[1] !== "1") {
      throw new Error("was made by a version this page doesn't know");
    }
    return globalThis.pako.inflateRaw(bytes(match[2]), { dictionary: DICTIONARY, to: "string" });
  }

  // Every marker in json, at any depth, with its compressed "gmv" made a plain "label"; how many were.
  function convertAll(json) {
    let count = 0;
    if (Array.isArray(json)) {
      json.forEach(item => count += convertAll(item));
    } else if (json && typeof json === "object") {
      if (typeof json.gmv === "string" && !json.gmv.includes("{")) {
        json.label = label(json.gmv);
        delete json.gmv;
        count++;
      }
      Object.values(json).forEach(value => count += convertAll(value));
    }
    return count;
  }

  // text converted: the JSON, as the plugin lays it out (a marker a line), and how many labels were converted.
  function convert(text) {
    const json = JSON.parse(text);
    const count = convertAll(json);
    const out = Array.isArray(json)
      ? (json.length ? "[\n  " + json.map(item => JSON.stringify(item)).join(",\n  ") + "\n]\n" : "[]")
      : JSON.stringify(json, null, 2);
    return { text: out, count: count };
  }

  globalThis.gmvConvert = convert;

  function attach() {
    const input = document.getElementById("gmv-old");
    if (!input || input.dataset.attached) {
      return;
    }
    input.dataset.attached = "true";
    const output = document.getElementById("gmv-new");
    const status = document.getElementById("gmv-status");
    document.getElementById("gmv-convert").addEventListener("click", () => {
      try {
        const result = convert(input.value);
        output.value = result.text;
        status.textContent = result.count
          ? "Converted " + result.count + " label" + (result.count === 1 ? "" : "s") + ". Copy it, then import it in the plugin."
          : "Nothing to convert: these markers aren't compressed, so the plugin imports them as they are.";
      } catch (e) {
        output.value = "";
        status.textContent = "Couldn't convert that: a label " + (e.message || "is damaged") + ". Paste the whole copied text.";
      }
    });
    document.getElementById("gmv-copy").addEventListener("click", () => navigator.clipboard.writeText(output.value));
  }

  if (typeof document !== "undefined") {
    // Material's instant loading swaps pages without reloading; otherwise, once loaded.
    if (typeof document$ !== "undefined") {
      document$.subscribe(attach);
    } else {
      document.addEventListener("DOMContentLoaded", attach);
    }
  }
})();
