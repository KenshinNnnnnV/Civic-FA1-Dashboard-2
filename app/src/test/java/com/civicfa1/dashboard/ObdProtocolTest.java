package com.civicfa1.dashboard;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

public class ObdProtocolTest {

    @Test public void parseIgnoresEchoAndKeepsFrame() {
        ObdProtocol.ParsedResponse p = ObdProtocol.parse("010C\r41 0C 1A F8\r>", "010C");
        assertTrue(p.promptSeen);
        assertEquals(1, p.frames.size());
        byte[] d = ObdProtocol.mode01Payload(p, 0x0C, 2);
        assertNotNull(d);
        assertEquals(0x1A, d[0] & 0xff);
        assertEquals(0xF8, d[1] & 0xff);
    }

    @Test public void malformedTextIsNotHarvestedAsHex() {
        ObdProtocol.ParsedResponse p = ObdProtocol.parse("BAD CAFE TEXT\r>", "010C");
        assertTrue(p.promptSeen);
        assertTrue(p.frames.isEmpty());
    }

    @Test public void partialResponseWithoutPromptIsMarkedIncomplete() {
        ObdProtocol.ParsedResponse p = ObdProtocol.parse("41 0D 28\r", "010D");
        assertFalse(p.promptSeen);
        assertEquals(1, p.frames.size());
    }

    @Test public void noDataIsTextStatusNotHex() {
        ObdProtocol.ParsedResponse p = ObdProtocol.parse("NO DATA\r>", "0105");
        assertEquals(ObdProtocol.TextStatus.NO_DATA, p.status);
        assertTrue(p.frames.isEmpty());
    }

    @Test public void supportedPidBitmapDecodesContinuationBit() {
        // Bit 32 set => continuation PID 0x20 supported.
        Set<Integer> pids = ObdProtocol.decodeSupportedPids(0x00, new byte[]{0, 0, 0, 1});
        assertTrue(pids.contains(0x20));
    }

    @Test public void mode01RequiresRequestedPayloadLength() {
        ObdProtocol.ParsedResponse p = ObdProtocol.parse("41 00 BE 3E\r>", "0100");
        assertNull(ObdProtocol.mode01Payload(p, 0x00, 4));
    }

    @Test public void mode03RequiresActual43Response() {
        ObdProtocol.DtcParseResult r = ObdProtocol.parseMode03(ObdProtocol.parse("NO DATA\r>", "03"));
        assertFalse(r.validMode03Response);
        assertTrue(r.codes.isEmpty());
    }

    @Test public void mode03EmptyValidReplyMeansNoStoredCodes() {
        ObdProtocol.DtcParseResult r = ObdProtocol.parseMode03(ObdProtocol.parse("43 00 00\r>", "03"));
        assertTrue(r.validMode03Response);
        assertTrue(r.codes.isEmpty());
    }

    @Test public void mode03DecodesP0300() {
        ObdProtocol.DtcParseResult r = ObdProtocol.parseMode03(ObdProtocol.parse("43 03 00\r>", "03"));
        assertTrue(r.validMode03Response);
        assertEquals(1, r.codes.size());
        assertEquals("P0300", r.codes.get(0));
    }

    @Test public void stoppedIsTextStatusNotPayload() {
        ObdProtocol.ParsedResponse p = ObdProtocol.parse("STOPPED\r>", "010C");
        assertEquals(ObdProtocol.TextStatus.STOPPED, p.status);
        assertTrue(p.frames.isEmpty());
    }

    @Test public void unableToConnectIsTextStatusNotPayload() {
        ObdProtocol.ParsedResponse p = ObdProtocol.parse("UNABLE TO CONNECT\r>", "0100");
        assertEquals(ObdProtocol.TextStatus.UNABLE_TO_CONNECT, p.status);
        assertTrue(p.frames.isEmpty());
    }

    @Test public void secondSupportedPidPageDecodes() {
        Set<Integer> pids = ObdProtocol.decodeSupportedPids(0x20, new byte[]{(byte) 0x80, 0, 0, 1});
        assertTrue(pids.contains(0x21));
        assertTrue(pids.contains(0x40));
    }

    @Test public void mode03DecodesMultipleCodes() {
        ObdProtocol.DtcParseResult r = ObdProtocol.parseMode03(ObdProtocol.parse("43 03 00 01 71\r>", "03"));
        assertTrue(r.validMode03Response);
        assertEquals(2, r.codes.size());
        assertEquals("P0300", r.codes.get(0));
        assertEquals("P0171", r.codes.get(1));
    }

    @Test public void readinessUsesSupportAndIncompleteBitsSeparately() {
        ObdManager.Readiness r = new ObdManager.Readiness();
        // Spark ignition. Misfire supported+complete; Fuel supported+incomplete; Catalyst supported+complete.
        byte[] data = new byte[]{0x00, 0x23, 0x01, 0x00};
        ObdManager.parseReadiness(r, data);
        assertEquals(ObdManager.MonitorState.COMPLETE, r.monitors.get("Misfire"));
        assertEquals(ObdManager.MonitorState.INCOMPLETE, r.monitors.get("Fuel System"));
        assertEquals(ObdManager.MonitorState.COMPLETE, r.monitors.get("Catalyst"));
    }
}
