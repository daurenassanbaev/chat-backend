package org.messenger.messagesvc.message.util.mapper;

import lombok.experimental.UtilityClass;
import org.messenger.messagesvc.caching.constants.RedisKeys;

import java.util.List;
import java.util.stream.Stream;

@UtilityClass
public class ChatKeyGenerator {

    public String generate(
            String user1,
            String user2
    ) {

        List<String> users =
                Stream.of(
                        user1,
                        user2
                )
                .sorted()
                .toList();

        return RedisKeys.LAST_MSG_KEY.formatted(
                users.get(0),
                users.get(1)
        );
    }
}