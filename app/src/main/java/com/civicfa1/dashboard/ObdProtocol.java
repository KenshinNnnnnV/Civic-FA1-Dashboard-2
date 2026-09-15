package com.civicfa1.dashboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pure-Java parsing helpers for ELM327/OBD responses. */
public final class ObdProtocol {
    private ObdProtocol() {}

    public enum TextStatus { NONE, OK, NO_DATA, STOPPED, UNABLE_TO_CONNECT, ERROR, SEARCHING }

    public static final class ParsedResponse {
        public final List<byte[]> frames;
        public final TextStatus status;
        public final boolean promptSeen;

        ParsedResponse(List<byte[]> frames, TextStatus status, boolean promptSeen) {
            this.frames = frames;
            this.status = status;
            this.promptSeen = promptSeen;
        }
    }

    public static ParsedResponse parse(String raw, String commandEcho) {
        if (raw == null) return new ParsedResponse(Collections.emptyList(), TextStatus.NONE, false);
        boolean prompt = raw.indexOf('>') >= 0;
        String echo = normalizeHexCommand(commandEcho);
        List<byte[]> frames = new ArrayList<>();
        TextStatus textStatus = TextStatus.NONE;

        String cleaned = raw.replace('>', '\n').replace('\r', '\n');
        String[] lines = cleaned.split("\\n+");
        for (String original : lines) {
            String line = original == null ? "" : original.trim();
            if (line.isEmpty()) continue;
            String compactUpper = line.toUpperCase(Locale.US).replace(" ", "").replace("\t", "");
            if (!echo.isEmpty() && compactUpper.equals(echo)) continue;

            String words = line.toUpperCase(Locale.US).replaceAll("\\s+", " ").trim();
            if (words.contains("UNABLE TO CONNECT")) { textStatus = TextStatus.UNABLE_TO_CONNECT; continue; }
            if (words.equals("NO DATA") || words.contains("NO DATA")) { textStatus = TextStatus.NO_DATA; continue; }
            if (words.equals("STOPPED") || words.contains("STOPPED")) { textStatus = TextStatus.STOPPED; continue; }
            if (words.equals("SEARCHING...") || words.startsWith("SEARCHING")) { textStatus = TextStatus.SEARCHING; continue; }
            if (words.equals("OK")) { textStatus = TextStatus.OK; continue; }
            if (words.equals("?") || words.contains("ERROR") || words.contains("BUS ERROR") || words.contains("CAN ERROR")) {
                textStatus = TextStatus.ERROR;
                continue;
            }

            byte[] bytes = parseHexLine(line);
            if (bytes != null && bytes.length > 0) frames.add(bytes);
        }
        return new ParsedResponse(frames, textStatus, prompt);
    }

    private static String normalizeHexCommand(String command) {
        if (command == null) return "";
        return command.toUpperCase(Locale.US).replaceAll("[^0-9A-F]", "");
    }

    /**
     * Strictly parse one response line. We accept either byte-separated hex ("41 0C 1A F8")
     * or one continuous even-length hex string ("410C1AF8"). We intentionally do not extract
     * hex characters from arbitrary text.
     */
    static byte[] parseHexLine(String line) {
        if (line == null) return null;
        String s = line.trim().toUpperCase(Locale.US);
        if (s.isEmpty()) return null;
        String[] tokens = s.split("[ \\t]+");
        if (tokens.length == 1) {
            String t = tokens[0];
            if (!t.matches("[0-9A-F]+") || (t.length() & 1) != 0 || t.length() < 2) return null;
            byte[] out = new byte[t.length() / 2];
            for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(t.substring(i * 2, i * 2 + 2), 16);
            return out;
        }
        // ATH0 is used, so normal frames should be two-digit byte tokens. Reject CAN headers here
        // rather than guessing which ECU a header belongs to.
        byte[] out = new byte[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            if (!tokens[i].matches("[0-9A-F]{2}")) return null;
            out[i] = (byte) Integer.parseInt(tokens[i], 16);
        }
        return out;
    }

    public static byte[] mode01Payload(ParsedResponse parsed, int pid, int minDataBytes) {
        if (parsed == null) return null;
        for (byte[] frame : parsed.frames) {
            if (frame.length < 2 + minDataBytes) continue;
            if ((frame[0] & 0xff) == 0x41 && (frame[1] & 0xff) == (pid & 0xff)) {
                byte[] payload = new byte[frame.length - 2];
                System.arraycopy(frame, 2, payload, 0, payload.length);
                return payload;
            }
        }
        return null;
    }

    public static Set<Integer> decodeSupportedPids(int basePid, byte[] payload4) {
        if (payload4 == null || payload4.length < 4) return Collections.emptySet();
        long bitmap = ((long) (payload4[0] & 0xff) << 24)
                | ((long) (payload4[1] & 0xff) << 16)
                | ((long) (payload4[2] & 0xff) << 8)
                | (long) (payload4[3] & 0xff);
        Set<Integer> out = new HashSet<>();
        for (int i = 0; i < 32; i++) {
            long mask = 1L << (31 - i);
            if ((bitmap & mask) != 0) out.add(basePid + i + 1);
        }
        return out;
    }

    public static final class DtcParseResult {
        public final boolean validMode03Response;
        public final List<String> codes;
        DtcParseResult(boolean validMode03Response, List<String> codes) {
            this.validMode03Response = validMode03Response;
            this.codes = codes;
        }
    }

    public static DtcParseResult parseMode03(ParsedResponse parsed) {
        if (parsed == null) return new DtcParseResult(false, Collections.emptyList());
        boolean found43 = false;
        List<String> out = new ArrayList<>();
        for (byte[] frame : parsed.frames) {
            if (frame.length < 1 || (frame[0] & 0xff) != 0x43) continue;
            found43 = true;
            for (int i = 1; i + 1 < frame.length; i += 2) {
                int a = frame[i] & 0xff;
                int b = frame[i + 1] & 0xff;
                if (a == 0 && b == 0) continue;
                char system = "PCBU".charAt((a >> 6) & 0x03);
                int d1 = (a >> 4) & 0x03;
                int d2 = a & 0x0f;
                out.add(String.format(Locale.US, "%c%d%X%02X", system, d1, d2, b));
            }
        }
        return new DtcParseResult(found43, out);
    }
}
