package com.groundmarkervariables.markers.minify;

import java.math.BigInteger;
import java.util.Arrays;

// Reads bytes written as one big number in ~3200 Western-looking letters and symbols (~11.7 bits per char), as older
// versions' compressed copies are. Never change: those must keep importing.
final class GmvAlphabet {
    // First/last char pairs.
    private static final String RANGES = "¡§©©¬¬®®°±¶·¿¿ÆÆÐÐ"
        + "×ØÞßææðð÷øþþĐđĦħıı"
        + "ĸĸŁłŊŋŒœŦŧƀƟƢƮƱǃǝǝ"
        + "ǤǥǶǷȜȝȠȠȢȥɐʭ͵͵ΑΡΣΩ"
        + "αωϗϯϳϳ϶϶ЂЂЄІЈЋЏИКи"
        + "кяђђєіјћџѵѸ҂ҊӀӃӎӔӕ"
        + "ӘәӠӡӨөԀԏԱՖՙ՟աֆ։֊ႠჅ"
        + "აჸ჻჻ᎠᏴᐁᙶᚁ᚜ᚠᛰ℄℄℈℈℔℔"
        + "℗℘℞℟℣℣℥℥℧℧℩℩℮℮ℲℲ℺℺"
        + "⅁⅄⅊⅋←↓↜↨↫↭↯⇌⇐∃∅∈∊∋"
        + "∍∣∥∥∧∫∮∮∱≀≂≃≅≆≈≈≊≟"
        + "≡≡≣≬≲≳≶≷≺≿⊂⊃⊆⊇⊊⊫⊰⋟"
        + "⋤⋩⋮⌙⌜⌧⌫⏎␀␦⑀⑊─▩▬▵▷▿"
        + "◁◺◿◿✁✁✃✄✆✇✎✎✐✑✓✓✕✕"
        + "✗✜✞✠✢✧✩✲✵❃❅❆❈❋❍❍❏❒"
        + "❖❖❘❞❡❢❥➔➘➠➢➯➱➾⟰⤳⤶⨋"
        + "⨍⩳⩷⫛⫝⫿";

    private static final char[] CHARS = chars();
    private static final BigInteger BASE = BigInteger.valueOf(CHARS.length);
    // Char -> its digit, or -1.
    private static final int[] INDEX = new int[Character.MAX_VALUE + 1];

    static {
        Arrays.fill(INDEX, -1);
        for (int i = 0; i < CHARS.length; i++) {
            INDEX[CHARS[i]] = i;
        }
    }

    private GmvAlphabet() {
    }

    private static char[] chars() {
        StringBuilder chars = new StringBuilder();
        for (int i = 0; i < RANGES.length(); i += 2) {
            for (char c = RANGES.charAt(i); c <= RANGES.charAt(i + 1); c++) {
                chars.append(c);
            }
        }
        return chars.toString().toCharArray();
    }

    // Throws IllegalArgumentException if text isn't one of these numbers (whose leading byte, 1, kept leading zero bytes).
    static byte[] decode(String text) {
        BigInteger value = BigInteger.ZERO;
        for (int i = 0; i < text.length(); i++) {
            int digit = INDEX[text.charAt(i)];
            if (digit < 0 || i == 0 && digit == 0) {
                throw new IllegalArgumentException("Not a digit at " + i);
            }
            value = value.multiply(BASE).add(BigInteger.valueOf(digit));
        }

        byte[] marked = value.toByteArray();
        if (marked.length == 0 || marked[0] != 1) {
            throw new IllegalArgumentException("Missing marker byte");
        }
        return Arrays.copyOfRange(marked, 1, marked.length);
    }
}
