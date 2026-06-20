package com.freshwater.report.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 读取 messages.yml，所有提示语支持 MiniMessage 格式（含 click/hover 标签）。
 */
public final class Messages {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, String> values;
    private final String prefix;

    private Messages(Map<String, String> values) {
        this.values = values;
        this.prefix = values.getOrDefault("prefix", "");
    }

    @SuppressWarnings("unchecked")
    public static Messages load(Path dataDir, Logger logger) throws IOException {
        Files.createDirectories(dataDir);
        Path file = dataDir.resolve("messages.yml");
        if (Files.notExists(file)) {
            try (InputStream in = Messages.class.getClassLoader().getResourceAsStream("messages.yml")) {
                if (in == null) {
                    throw new IOException("缺少内置资源: messages.yml");
                }
                try (OutputStream out = Files.newOutputStream(file)) {
                    in.transferTo(out);
                }
            }
            logger.info("已生成默认 messages.yml。");
        }

        Map<String, String> flat = new LinkedHashMap<>();
        try (InputStream in = Files.newInputStream(file)) {
            Object loaded = new Yaml().load(in);
            if (loaded instanceof Map) {
                flatten("", (Map<String, Object>) loaded, flat);
            }
        }
        return new Messages(flat);
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> map, Map<String, String> out) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flatten(key, (Map<String, Object>) value, out);
            } else if (value != null) {
                out.put(key, String.valueOf(value));
            }
        }
    }

    public String raw(String key) {
        return values.getOrDefault(key, key);
    }

    /** 渲染为组件（不带前缀）。 */
    public Component render(String key, TagResolver... resolvers) {
        return miniMessage.deserialize(raw(key), resolvers);
    }

    /** 渲染为组件并加上 prefix。 */
    public Component prefixed(String key, TagResolver... resolvers) {
        return miniMessage.deserialize(prefix + raw(key), resolvers);
    }

    /** 直接反序列化一段 MiniMessage 文本。 */
    public Component deserialize(String miniMessageText, TagResolver... resolvers) {
        return miniMessage.deserialize(miniMessageText, resolvers);
    }

    public Component withPrefix(Component component) {
        return miniMessage.deserialize(prefix).append(component);
    }

    public MiniMessage miniMessage() {
        return miniMessage;
    }
}
