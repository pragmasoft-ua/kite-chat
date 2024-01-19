/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package kite.core.application.telegram;

import static kite.core.domain.payload.SendText.Mode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.MessageEntity;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.*;
import java.util.Random;
import java.util.stream.Stream;
import kite.core.domain.payload.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TgUtilsTest extends TelegramBaseTest {

  public static Stream<Arguments> payloadSource() {
    return Stream.of(
        Arguments.of(TG_SEND_BINARY, CopyMessage.class),
        Arguments.of(TG_SEND_TEXT, CopyMessage.class),
        Arguments.of(NEW_SEND_TEXT, SendMessage.class),
        Arguments.of(NEW_SEND_DOCUMENT, SendDocument.class),
        Arguments.of(NEW_SEND_PHOTO, SendPhoto.class),
        Arguments.of(EDIT_SEND_TEXT, EditMessageText.class),
        Arguments.of(EDIT_SEND_DOCUMENT, EditMessageMedia.class),
        Arguments.of(EDIT_SEND_PHOTO, EditMessageMedia.class),
        Arguments.of(DELETE_MESSAGE, com.pengrad.telegrambot.request.DeleteMessage.class),
        Arguments.of(NOTIFICATION, SendMessage.class),
        Arguments.of(ERROR, SendMessage.class));
  }

  @ParameterizedTest(name = "Should parse (from -> to) {argumentsWithNames}")
  @DisplayName(
      "Should parse different Payloads and return appropriate Telegram Request on toRequest()")
  @MethodSource("payloadSource")
  void should_parse_different_payloads_to_specific_telegram_request(
      Payload payload, Class<?> response) {
    var request = TgUtils.toRequest(CHAT_ID, payload);

    assertThat(request).isPresent().get().isInstanceOf(response);
  }

  public static Stream<Arguments> nameSource() {
    return Stream.of(
        Arguments.of("Test", "Member", "Test Member"),
        Arguments.of("Test", "", "Test"),
        Arguments.of("", "Member", "Member"),
        Arguments.of(null, null, "Username"));
  }

  @ParameterizedTest
  @DisplayName("Should retrieve appropriate username for User on userName()")
  @MethodSource("nameSource")
  void should_retrieve_username_from_update(String firstname, String lastname, String expected) {
    User user = mock(User.class);
    doReturn("Username").when(user).username();
    doReturn(firstname).when(user).firstName();
    doReturn(lastname).when(user).lastName();

    String name = TgUtils.userName(user);

    assertThat(name).isEqualTo(expected);
  }

  @ParameterizedTest
  @DisplayName(
      "Should retrieve memberId from hashtag if there are several MessageEntities"
          + " memberIdFromHashTag()")
  @ValueSource(ints = {4, 8, 9, 11, 13})
  void should_retrieve_memberId_from_hashtag_in_text(int size) {
    String randomId = randomId(size);
    Message message = mock(Message.class);
    MessageEntity messageEntity1 = mock(MessageEntity.class);
    MessageEntity messageEntity2 = mock(MessageEntity.class);
    MessageEntity messageEntity3 = mock(MessageEntity.class);

    doReturn(new MessageEntity[] {messageEntity1, messageEntity2, messageEntity3})
        .when(message)
        .entities();
    doReturn(MessageEntity.Type.code).when(messageEntity1).type();
    doReturn(MessageEntity.Type.phone_number).when(messageEntity2).type();
    doReturn(MessageEntity.Type.hashtag).when(messageEntity3).type();
    doReturn(0).when(messageEntity3).offset();
    doReturn(size + 1).when(messageEntity3).length();
    doReturn("#" + randomId).when(message).text();

    var memberId = TgUtils.memberIdFromHashTag(message);

    assertThat(memberId).isPresent().get().isEqualTo(randomId);
  }

  @ParameterizedTest
  @DisplayName(
      "Should retrieve memberId from hashtag if there are several MessageEntities"
          + " memberIdFromHashTag()")
  @ValueSource(ints = {5, 6, 7, 10, 12})
  void should_retrieve_memberId_from_hashtag_in_caption(int size) {
    String randomId = randomId(size);
    Message message = mock(Message.class);
    MessageEntity messageEntity1 = mock(MessageEntity.class);
    MessageEntity messageEntity2 = mock(MessageEntity.class);

    doReturn(
            new MessageEntity[] {
              messageEntity1, messageEntity2,
            })
        .when(message)
        .captionEntities();
    doReturn(MessageEntity.Type.code).when(messageEntity1).type();
    doReturn(MessageEntity.Type.hashtag).when(messageEntity2).type();
    doReturn(0).when(messageEntity2).offset();
    doReturn(size + 1).when(messageEntity2).length();
    doReturn("#" + randomId).when(message).caption();

    var memberId = TgUtils.memberIdFromHashTag(message);

    assertThat(memberId).isPresent().get().isEqualTo(randomId);
  }

  @Test
  @DisplayName("Should return empty if message in null memberIdFromHashTag()")
  void should_return_empty_if_message_is_null() {
    var memberId = TgUtils.memberIdFromHashTag(null);

    assertThat(memberId).isEmpty();
  }

  @Test
  @DisplayName("Should return empty if there are no entities on memberIdFromHashTag()")
  void should_return_empty_if_entities_are_null() {
    Message message = mock(Message.class);
    doReturn(null).when(message).entities();
    doReturn(null).when(message).captionEntities();

    var memberId = TgUtils.memberIdFromHashTag(message);

    assertThat(memberId).isEmpty();
  }

  @Test
  @DisplayName("Should return true if message contains command on startsFromCommandEntity()")
  void should_true_if_message_contains_command() {
    Message message = mock(Message.class);
    MessageEntity messageEntity = mock(MessageEntity.class);

    doReturn(new MessageEntity[] {messageEntity}).when(message).entities();
    doReturn(MessageEntity.Type.bot_command).when(messageEntity).type();

    boolean isCommand = TgUtils.startsFromCommandEntity(message);

    Assertions.assertTrue(isCommand);
  }

  private String randomId(int size) {
    int leftLimit = 97;
    int rightLimit = 122;
    Random random = new Random();
    StringBuilder buffer = new StringBuilder(size);
    for (int i = 0; i < size; i++) {
      int randomLimitedInt = leftLimit + (int) (random.nextFloat() * (rightLimit - leftLimit + 1));
      buffer.append((char) randomLimitedInt);
    }
    return buffer.toString();
  }
}
