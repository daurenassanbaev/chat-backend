package org.chat.shared.caching;

public final class PendingMessageScripts {
    private PendingMessageScripts() {}
    private static final String MIGRATE = """
        local kind = redis.call('TYPE', KEYS[1]).ok
        if kind == 'list' then
            local values = redis.call('LRANGE', KEYS[1], 0, -1)
            local ids = {}
            for i, value in ipairs(values) do
                local message = cjson.decode(value)
                if not message.messageId or message.messageId == cjson.null then
                    return redis.error_reply('Legacy message has no messageId')
                end
                ids[i] = message.messageId
            end
            redis.call('DEL', KEYS[1])
            for i, value in ipairs(values) do
                redis.call('HSET', KEYS[1], ids[i], value)
            end
            redis.call('EXPIRE', KEYS[1], 2592000)
        elseif kind ~= 'none' and kind ~= 'hash' then
            return redis.error_reply('Unexpected pending key type: ' .. kind)
        end
        """;
    public static final String SAVE = MIGRATE + """
        redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
        redis.call('EXPIRE', KEYS[1], 2592000)
        return 1
        """;
    public static final String READ = MIGRATE + "return redis.call('HVALS', KEYS[1])";
}
