package sample.chirper.friend.api;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.restCall;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;
import org.pcollections.PSequence;

/**
 * The friend service.
 */
public interface FriendService extends Service {

  /**
   * Service call for getting a user.
   *
   * The ID of this service call is the user name, and the response message is the User object.
   */
  ServiceCall<String, NotUsed, User> getUser();

  /**
   * Service call for creating a user.
   *
   * The request message is the User to create.
   */
  ServiceCall<NotUsed, User, NotUsed> createUser();

  /**
   * Service call for adding a friend to a user.
   *
   * The ID for this service call is the ID of the user that the friend is being added to.
   * The request message is the ID of the friend being added.
   */
  ServiceCall<String, FriendId, NotUsed> addFriend();

  /**
   * Service call for getting the followers of a user.
   *
   * The ID for this service call is the Id of the user to get the followers for.
   * The response message is the list of follower IDs.
   */
  ServiceCall<String, NotUsed, PSequence<String>> getFollowers();

  @Override
  default Descriptor descriptor() {
    // @formatter:off
    return named("friendservice").with(
        restCall(Method.GET,  "/api/users/:id", getUser()),
        restCall(Method.POST, "/api/users", createUser()),
        restCall(Method.POST, "/api/users/:userId/friends", addFriend()),
        restCall(Method.GET,  "/api/users/:id/followers", getFollowers())
      ).withAutoAcl(true);
    // @formatter:on
  }
}
