package org.chat.shared.caching;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;


@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RedisConstants {

    // TEMPLATE: {domain}:{entity}:{id}
    public static final String ONLINE_KEY  = "online:%s";
    public static final String OFFLINE_KEY  = "offline:%s";
    public static final String MSG_KEY  = "msg:%s";

    public static final String DUMMY_VALUE = "1";

}