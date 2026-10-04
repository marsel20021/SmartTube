package com.liskovsoft.smartyoutubetv2.tv.ui.playback.ambilight;

import android.os.SystemClock;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Отправка кадра в WLED по протоколу DDP (UDP).
 * Количество светодиодов не ограничено: при необходимости кадр автоматически делится на несколько
 * пакетов (до 480 светодиодов в пакете), последний пакет несёт флаг PUSH.
 * Все буферы переиспользуются, адрес хоста кэшируется.
 */
final class DdpSender {
    private static final String TAG = "AmbilightDdp";
    private static final int HEADER_SIZE = 10;
    private static final int MAX_LEDS_PER_PACKET = 480;           // 1440 байт данных, влезает в MTU
    private static final long RESOLVE_RETRY_MS = 3000;

    private DatagramSocket socket;
    private final byte[] packet = new byte[HEADER_SIZE + MAX_LEDS_PER_PACKET * 3];
    private final DatagramPacket datagram = new DatagramPacket(packet, packet.length);

    private String cachedHost;
    private InetAddress cachedAddress;
    private long nextResolveAt;

    DdpSender() {
        try {
            socket = new DatagramSocket();
        } catch (Exception e) {
            Log.w(TAG, "Не удалось открыть UDP сокет", e);
        }
    }

    boolean send(String host, int port, byte[] rgb, int ledCount) {
        if (socket == null || ledCount <= 0) return false;
        InetAddress address = resolve(host);
        if (address == null) return false;

        datagram.setAddress(address);
        datagram.setPort(port);

        try {
            int sent = 0;
            while (sent < ledCount) {
                int chunk = Math.min(MAX_LEDS_PER_PACKET, ledCount - sent);
                boolean last = sent + chunk >= ledCount;
                int offset = sent * 3;
                int length = chunk * 3;

                packet[0] = (byte) (0x40 | (last ? 0x01 : 0x00));   // версия 1 + PUSH на последнем
                packet[1] = 0;
                packet[2] = 0x01;
                packet[3] = 0x01;
                packet[4] = (byte) (offset >>> 24);
                packet[5] = (byte) (offset >>> 16);
                packet[6] = (byte) (offset >>> 8);
                packet[7] = (byte) offset;
                packet[8] = (byte) (length >>> 8);
                packet[9] = (byte) length;
                System.arraycopy(rgb, offset, packet, HEADER_SIZE, length);

                datagram.setLength(HEADER_SIZE + length);
                socket.send(datagram);
                sent += chunk;
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Ошибка отправки DDP", e);
            return false;
        }
    }

    /** Погасить все светодиоды. */
    void sendBlank(String host, int port, int ledCount) {
        if (ledCount > 0) send(host, port, new byte[ledCount * 3], ledCount);
    }

    void close() {
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }

    private InetAddress resolve(String host) {
        if (host == null || host.isEmpty()) return null;
        if (host.equals(cachedHost) && cachedAddress != null) return cachedAddress;

        long now = SystemClock.uptimeMillis();
        if (host.equals(cachedHost) && now < nextResolveAt) return null;   // недавно не получилось
        cachedHost = host;
        try {
            cachedAddress = InetAddress.getByName(host);
        } catch (Exception e) {
            cachedAddress = null;
            nextResolveAt = now + RESOLVE_RETRY_MS;
            Log.w(TAG, "Не удалось определить адрес: " + host);
        }
        return cachedAddress;
    }
}