package sgms.sgmscraft.util;

import java.util.ArrayList;
import java.util.List;

public final class Text {
    private Text() {}

    /** '&' colour codes and '&#RRGGBB' hex colours -> legacy section codes. */
    public static String color(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 8 <= s.length() && s.charAt(i + 1) == '#' && isHex(s.substring(i + 2, i + 8))) {
                out.append('§').append('x');
                for (char h : s.substring(i + 2, i + 8).toCharArray()) out.append('§').append(h);
                i += 7;
            } else if (c == '&' && i + 1 < s.length() && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(s.charAt(i + 1)) >= 0) {
                out.append('§').append(Character.toLowerCase(s.charAt(i + 1)));
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean isHex(String s) {
        for (char c : s.toCharArray()) if ("0123456789abcdefABCDEF".indexOf(c) < 0) return false;
        return true;
    }

    public static List<String> color(List<String> in) {
        List<String> out = new ArrayList<>();
        if (in != null) for (String s : in) out.add(color(s));
        return out;
    }

    /** Section codes back to '&' form (hex §x§R§R§G§G§B§B -> &#RRGGBB) for saving into configs. */
    public static String uncolor(String s) {
        if (s == null) return null;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 13 < s.length() + 0 && Character.toLowerCase(s.charAt(i + 1)) == 'x') {
                StringBuilder hex = new StringBuilder();
                boolean ok = true;
                for (int k = 0; k < 6; k++) {
                    int pos = i + 2 + k * 2;
                    if (pos + 1 >= s.length() || s.charAt(pos) != '§') { ok = false; break; }
                    hex.append(s.charAt(pos + 1));
                }
                if (ok) {
                    out.append("&#").append(hex);
                    i += 13;
                    continue;
                }
            }
            out.append(c == '§' ? '&' : c);
        }
        return out.toString();
    }

    /** Wraps long text into lore lines. */
    public static List<String> wrap(String text, int width, String prefix) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : text.split(" ")) {
            if (line.length() > 0 && line.length() + w.length() + 1 > width) {
                out.add(prefix + line);
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(w);
        }
        if (line.length() > 0) out.add(prefix + line);
        return out;
    }

    public static String strip(String s) {
        if (s == null) return "";
        return s.replaceAll("§.", "");
    }

    /** progress bar like ▮▮▮▮▯▯▯ */
    public static String bar(double fraction, int len, String full, String empty) {
        fraction = Math.max(0, Math.min(1, fraction));
        int n = (int) Math.round(fraction * len);
        StringBuilder b = new StringBuilder();
        b.append(full);
        for (int i = 0; i < n; i++) b.append('▮');
        b.append(empty);
        for (int i = n; i < len; i++) b.append('▮');
        return b.toString();
    }

    public static String seconds(int ticks) {
        return Math.max(0, (ticks + 19) / 20) + "с";
    }
}
