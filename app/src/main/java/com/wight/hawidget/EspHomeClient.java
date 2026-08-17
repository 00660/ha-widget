package com.wight.hawidget;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

final class EspHomeClient {
    private static final String HOST = "192.168.2.199";
    private static final int PORT = 6053;
    private static final int FAN_KEY = 15975543;
    private static final int TIMEOUT_MILLIS = 3000;

    private EspHomeClient() {
    }

    static FanState fetchFanState() throws IOException {
        try (Session session = Session.open()) {
            session.send(20, new byte[0]);
            return session.readFanState();
        }
    }

    static void toggleFan() throws IOException {
        FanState state = fetchFanState();
        sendCommand(commandWithState(!state.on));
    }

    static void setFanPercentage(int percentage) throws IOException {
        ByteArrayOutputStream command = newCommand();
        writeBoolean(command, 10, true);
        writeVarIntField(command, 11, Math.max(0, Math.min(100, percentage)));
        sendCommand(command.toByteArray());
    }

    static void setFanPreset(String preset) throws IOException {
        ByteArrayOutputStream command = newCommand();
        writeBoolean(command, 12, true);
        writeString(command, 13, preset);
        sendCommand(command.toByteArray());
    }

    private static void sendCommand(byte[] command) throws IOException {
        try (Session session = Session.open()) {
            session.send(31, command);
        }
    }

    private static byte[] commandWithState(boolean on) {
        ByteArrayOutputStream command = newCommand();
        writeBoolean(command, 2, true);
        writeBoolean(command, 3, on);
        return command.toByteArray();
    }

    private static ByteArrayOutputStream newCommand() {
        ByteArrayOutputStream command = new ByteArrayOutputStream();
        command.write(0x0D);
        command.write(FAN_KEY & 0xFF);
        command.write((FAN_KEY >>> 8) & 0xFF);
        command.write((FAN_KEY >>> 16) & 0xFF);
        command.write((FAN_KEY >>> 24) & 0xFF);
        return command;
    }

    private static void writeBoolean(ByteArrayOutputStream output, int field, boolean value) {
        writeVarInt(output, field << 3);
        writeVarInt(output, value ? 1 : 0);
    }

    private static void writeVarIntField(ByteArrayOutputStream output, int field, int value) {
        writeVarInt(output, field << 3);
        writeVarInt(output, value);
    }

    private static void writeString(ByteArrayOutputStream output, int field, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(output, (field << 3) | 2);
        writeVarInt(output, bytes.length);
        output.write(bytes, 0, bytes.length);
    }

    private static void writeVarInt(OutputStream output, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            output.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        output.write(value);
    }

    private static void writeVarInt(ByteArrayOutputStream output, int value) {
        while ((value & ~0x7F) != 0) {
            output.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        output.write(value);
    }

    static final class FanState {
        final boolean on;
        final boolean available;
        final int percentage;
        final String presetMode;

        FanState(boolean on, boolean available, int percentage, String presetMode) {
            this.on = on;
            this.available = available;
            this.percentage = percentage;
            this.presetMode = presetMode;
        }
    }

    private static final class Session implements AutoCloseable {
        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;

        private Session(Socket socket) throws IOException {
            this.socket = socket;
            input = socket.getInputStream();
            output = socket.getOutputStream();
        }

        static Session open() throws IOException {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(HOST, PORT), TIMEOUT_MILLIS);
            socket.setSoTimeout(TIMEOUT_MILLIS);
            Session session = new Session(socket);
            try {
                session.send(1, helloRequest());
                Frame response = session.read();
                if (response.type != 2) {
                    throw new IOException("ESPHome handshake failed");
                }
                return session;
            } catch (IOException exception) {
                socket.close();
                throw exception;
            }
        }

        private static byte[] helloRequest() {
            ByteArrayOutputStream hello = new ByteArrayOutputStream();
            writeString(hello, 1, "HA Fan Widget");
            writeVarIntField(hello, 2, 1);
            writeVarIntField(hello, 3, 15);
            return hello.toByteArray();
        }

        void send(int type, byte[] body) throws IOException {
            output.write(0);
            writeVarInt(output, body.length);
            writeVarInt(output, type);
            output.write(body);
            output.flush();
        }

        FanState readFanState() throws IOException {
            while (true) {
                Frame frame = read();
                if (frame.type == 23) {
                    FanState state = parseFanState(frame.body);
                    if (state != null) {
                        return state;
                    }
                }
            }
        }

        private Frame read() throws IOException {
            if (input.read() != 0) {
                throw new IOException("Invalid ESPHome frame");
            }
            int length = readVarInt(input);
            int type = readVarInt(input);
            byte[] body = readFully(input, length);
            return new Frame(type, body);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static FanState parseFanState(byte[] data) throws IOException {
        Cursor cursor = new Cursor();
        int key = 0;
        boolean on = false;
        int percentage = -1;
        String presetMode = "";
        while (cursor.position < data.length) {
            int tag = readVarInt(data, cursor);
            int field = tag >>> 3;
            int wireType = tag & 7;
            if (field == 1 && wireType == 5) {
                key = readFixed32(data, cursor);
            } else if (field == 2 && wireType == 0) {
                on = readVarInt(data, cursor) != 0;
            } else if (field == 6 && wireType == 0) {
                percentage = readVarInt(data, cursor);
            } else if (field == 7 && wireType == 2) {
                presetMode = readString(data, cursor);
            } else {
                skipField(data, cursor, wireType);
            }
        }
        return key == FAN_KEY ? new FanState(on, true, percentage, presetMode) : null;
    }

    private static int readVarInt(InputStream input) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 28; shift += 7) {
            int next = input.read();
            if (next < 0) {
                throw new IOException("Unexpected end of ESPHome stream");
            }
            value |= (next & 0x7F) << shift;
            if ((next & 0x80) == 0) {
                return value;
            }
        }
        throw new IOException("Invalid ESPHome varint");
    }

    private static byte[] readFully(InputStream input, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = input.read(data, offset, length - offset);
            if (count < 0) {
                throw new IOException("Unexpected end of ESPHome frame");
            }
            offset += count;
        }
        return data;
    }

    private static int readVarInt(byte[] data, Cursor cursor) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 28; shift += 7) {
            if (cursor.position >= data.length) {
                throw new IOException("Unexpected end of ESPHome message");
            }
            int next = data[cursor.position++] & 0xFF;
            value |= (next & 0x7F) << shift;
            if ((next & 0x80) == 0) {
                return value;
            }
        }
        throw new IOException("Invalid ESPHome varint");
    }

    private static int readFixed32(byte[] data, Cursor cursor) throws IOException {
        if (cursor.position + 4 > data.length) {
            throw new IOException("Unexpected end of ESPHome fixed32");
        }
        int value = (data[cursor.position] & 0xFF)
                | ((data[cursor.position + 1] & 0xFF) << 8)
                | ((data[cursor.position + 2] & 0xFF) << 16)
                | ((data[cursor.position + 3] & 0xFF) << 24);
        cursor.position += 4;
        return value;
    }

    private static String readString(byte[] data, Cursor cursor) throws IOException {
        int length = readVarInt(data, cursor);
        if (length < 0 || cursor.position + length > data.length) {
            throw new IOException("Invalid ESPHome string");
        }
        String value = new String(data, cursor.position, length, StandardCharsets.UTF_8);
        cursor.position += length;
        return value;
    }

    private static void skipField(byte[] data, Cursor cursor, int wireType) throws IOException {
        if (wireType == 0) {
            readVarInt(data, cursor);
        } else if (wireType == 1) {
            cursor.position += 8;
        } else if (wireType == 2) {
            int length = readVarInt(data, cursor);
            cursor.position += length;
        } else if (wireType == 5) {
            cursor.position += 4;
        } else {
            throw new IOException("Unsupported ESPHome wire type");
        }
        if (cursor.position > data.length) {
            throw new IOException("Invalid ESPHome field length");
        }
    }

    private static final class Cursor {
        int position;
    }

    private static final class Frame {
        final int type;
        final byte[] body;

        Frame(int type, byte[] body) {
            this.type = type;
            this.body = body;
        }
    }
}
