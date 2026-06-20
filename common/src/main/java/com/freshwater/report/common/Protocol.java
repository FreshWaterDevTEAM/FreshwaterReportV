package com.freshwater.report.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * 跨代理插件消息的简单编解码协议。
 *
 * <p>报文结构：UTF(type) + int(参数个数) + N * UTF(参数)。</p>
 */
public final class Protocol {

    /** Velocity -> Waterfall：请求把处理者(staff)传送到被举报者(target)所在子服。args: [staffUuid, targetUuid] */
    public static final String TP_TO_PLAYER = "TP_TO_PLAYER";

    /** Velocity -> Waterfall：查询某玩家当前真实子服。args: [targetUuid] */
    public static final String QUERY_LOCATION = "QUERY_LOCATION";

    /** Waterfall -> Velocity：QUERY_LOCATION 的应答。args: [targetUuid, serverName]（serverName 为空表示离线/未知） */
    public static final String LOCATION = "LOCATION";

    /** Waterfall -> Velocity：玩家切换子服时主动上报。args: [targetUuid, serverName] */
    public static final String LOCATION_UPDATE = "LOCATION_UPDATE";

    private Protocol() {
    }

    public static byte[] encode(String type, String... args) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            out.writeUTF(type);
            out.writeInt(args.length);
            for (String arg : args) {
                out.writeUTF(arg == null ? "" : arg);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return baos.toByteArray();
    }

    public static Message decode(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            String type = in.readUTF();
            int count = in.readInt();
            String[] args = new String[Math.max(0, count)];
            for (int i = 0; i < count; i++) {
                args[i] = in.readUTF();
            }
            return new Message(type, args);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 解码后的消息。 */
    public static final class Message {
        private final String type;
        private final String[] args;

        public Message(String type, String[] args) {
            this.type = type;
            this.args = args;
        }

        public String type() {
            return type;
        }

        public String[] args() {
            return args;
        }

        public String arg(int index) {
            return index >= 0 && index < args.length ? args[index] : null;
        }
    }
}
