/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package kite.core.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import kite.core.domain.Connection.ChannelConnection;
import kite.core.domain.Connection.MemberConnection;

class KiteCoreBaseServiceTest {
  protected static final String CHANNEL_NAME = "k1te_test";
  protected static final Route CHANNEL_ORIGIN = Route.of("tg:channelId");
  protected static final String HOST_ID = "hostId";

  protected static final String MEMBER_RAW_ID = "memberId";
  protected static final Member.Id MEMBER_ID = new Member.Id(CHANNEL_NAME, MEMBER_RAW_ID);
  protected static final Route MEMBER_ORIGIN = Route.of("ws:memberId");
  protected static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("en");
  protected static final String MEMBER_NAME = "test_user";

  protected static final Member MEMBER = defaultMemberInstance();
  protected static final Channel CHANNEL = defaultChannelInstance();

  protected static final MemberConnection MEMBER_CONNECTION = defaultMemberConnection();
  protected static final ChannelConnection CHANNEL_CONNECTION = defaultChannelConnection();

  private static Member defaultMemberInstance() {
    return new Member(MEMBER_ID, MEMBER_NAME, Set.of(MEMBER_ORIGIN));
  }

  private static Channel defaultChannelInstance() {
    return new Channel(
        CHANNEL_NAME, HOST_ID, CHANNEL_ORIGIN, Optional.empty(), Optional.of(MEMBER_ID));
  }

  private static MemberConnection defaultMemberConnection() {
    return new MemberConnection(MEMBER_ORIGIN, MEMBER_ID);
  }

  private static ChannelConnection defaultChannelConnection() {
    return new ChannelConnection(CHANNEL_ORIGIN, CHANNEL_NAME);
  }
}
