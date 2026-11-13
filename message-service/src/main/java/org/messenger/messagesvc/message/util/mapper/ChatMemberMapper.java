package org.messenger.messagesvc.message.util.mapper;

import lombok.experimental.UtilityClass;
import org.messenger.messagesvc.message.dto.ChatRecipientDto;
import org.messenger.messagesvc.message.entity.ChatMember;

@UtilityClass
public class ChatMemberMapper {

    public ChatRecipientDto toChatRecipientDto(ChatMember chatMember) {
        return new ChatRecipientDto(chatMember.getId().getUserId());
    }
}
