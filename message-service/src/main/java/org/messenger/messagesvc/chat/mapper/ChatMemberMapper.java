package org.messenger.messagesvc.chat.mapper;

import lombok.experimental.UtilityClass;
import org.messenger.messagesvc.chat.dto.ChatRecipientDto;
import org.messenger.messagesvc.chat.entity.ChatMember;

@UtilityClass
public class ChatMemberMapper {

    public ChatRecipientDto toChatRecipientDto(ChatMember chatMember) {
        return new ChatRecipientDto(chatMember.getId().getUserId());
    }
}
