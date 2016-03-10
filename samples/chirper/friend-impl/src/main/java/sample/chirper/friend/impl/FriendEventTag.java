package sample.chirper.friend.impl;

import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;

public class FriendEventTag {

  public static final AggregateEventTag<FriendEvent> INSTANCE = 
    AggregateEventTag.of(FriendEvent.class);

}
