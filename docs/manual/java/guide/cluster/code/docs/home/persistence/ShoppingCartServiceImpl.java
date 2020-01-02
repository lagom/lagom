/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

import akka.Done;
import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.japi.Pair;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;
import static com.lightbend.lagom.javadsl.api.Service.*;
import com.lightbend.lagom.javadsl.api.transport.BadRequest;
import com.lightbend.lagom.javadsl.api.transport.NotFound;
import com.lightbend.lagom.javadsl.broker.TopicProducer;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import javax.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

interface ShoppingCartService extends Service {

  ServiceCall<NotUsed, ShoppingCartView> get(String id);

  ServiceCall<ShoppingCartItem, Done> addItem(String id);

  ServiceCall<NotUsed, Done> checkout(String id);

  @Override
  default Descriptor descriptor() {
    return named("shopping-cart")
        .withCalls(
            restCall(Method.GET, "/shoppingcart/:id", this::get),
            restCall(Method.POST, "/shoppingcart/:id", this::addItem),
            restCall(Method.POST, "/shoppingcart/:id/checkout", this::checkout))
        .withAutoAcl(true);
  }

  /** A shopping cart. */
  // #shopping-cart-service-view
  @Value
  @JsonDeserialize
  public final class ShoppingCartView {
    /** The ID of the shopping cart. */
    public final String id;

    /** The list of items in the cart. */
    public final List<ShoppingCartItem> items;

    /** Whether this cart has been checked out. */
    public final boolean checkedOut;

    /** When this cart was checked out. */
    public final Optional<Instant> checkedOutTime;

    @JsonCreator
    public ShoppingCartView(
        String id, List<ShoppingCartItem> items, Optional<Instant> checkedOutTime) {
      this.id = Preconditions.checkNotNull(id, "id");
      this.items = Preconditions.checkNotNull(items, "items");
      this.checkedOutTime = checkedOutTime;
      this.checkedOut = checkedOutTime.isPresent();
    }

    public boolean hasItem(String itemId) {
      return items.stream().anyMatch(item -> item.getItemId().equals(itemId));
    }

    public Optional<ShoppingCartItem> get(String itemId) {
      return items.stream().filter(item -> item.getItemId().equals(itemId)).findFirst();
    }
  }
  // #shopping-cart-service-view

  /** An item in a shopping cart. */
  @Value
  @JsonDeserialize
  public final class ShoppingCartItem {
    /** The ID of the product. */
    public final String itemId;
    /** The quantity of this product in the cart. */
    public final int quantity;

    @JsonCreator
    public ShoppingCartItem(String itemId, int quantity) {
      this.itemId = Preconditions.checkNotNull(itemId, "productId");
      this.quantity = quantity;
    }
  }
}

/** Implementation of the {@link ShoppingCartService}. */
public class ShoppingCartServiceImpl implements ShoppingCartService {

  private final ClusterSharding clusterSharing;

  // #shopping-cart-init
  @Inject
  public ShoppingCartServiceImpl(ClusterSharding clusterSharing) {
    this.clusterSharing = clusterSharing;

    // register entity on shard
    this.clusterSharing.init(
        Entity.of(
            ShoppingCartEntity.ENTITY_TYPE_KEY, // <- type key
            ShoppingCartEntity::create // <- create function
            ));
  }
  // #shopping-cart-init

  // #shopping-cart-entity-ref
  private EntityRef<ShoppingCartEntity.Command> entityRef(String id) {
    return clusterSharing.entityRefFor(ShoppingCartEntity.ENTITY_TYPE_KEY, id);
  }
  // #shopping-cart-entity-ref

  // #shopping-cart-service-call
  private final Duration askTimeout = Duration.ofSeconds(5);

  @Override
  public ServiceCall<NotUsed, ShoppingCartView> get(String id) {
    return request ->
        entityRef(id)
            .<ShoppingCartEntity.Summary>ask(
                replyTo -> new ShoppingCartEntity.Get(replyTo), askTimeout)
            .thenApply(summary -> asShoppingCartView(id, summary));
  }
  // #shopping-cart-service-call

  @Override
  public ServiceCall<ShoppingCartItem, Done> addItem(String cartId) {
    return item ->
        entityRef(cartId)
            .<ShoppingCartEntity.Confirmation>ask(
                replyTo ->
                    new ShoppingCartEntity.AddItem(item.getItemId(), item.getQuantity(), replyTo),
                askTimeout)
            .thenApply(this::handleConfirmation)
            .thenApply(accepted -> Done.getInstance());
  }

  @Override
  public ServiceCall<NotUsed, Done> checkout(String cartId) {
    return request ->
        entityRef(cartId)
            .ask(ShoppingCartEntity.Checkout::new, askTimeout)
            .thenApply(this::handleConfirmation)
            .thenApply(accepted -> Done.getInstance());
  }

  private ShoppingCartEntity.Accepted handleConfirmation(
      ShoppingCartEntity.Confirmation confirmation) {
    if (confirmation instanceof ShoppingCartEntity.Accepted) {
      ShoppingCartEntity.Accepted accepted = (ShoppingCartEntity.Accepted) confirmation;
      return accepted;
    }

    ShoppingCartEntity.Rejected rejected = (ShoppingCartEntity.Rejected) confirmation;
    throw new BadRequest(rejected.getReason());
  }

  // #shopping-cart-service-map
  private ShoppingCartView asShoppingCartView(String id, ShoppingCartEntity.Summary summary) {
    List<ShoppingCartItem> items = new ArrayList<>();
    for (Map.Entry<String, Integer> item : summary.getItems().entrySet()) {
      items.add(new ShoppingCartItem(item.getKey(), item.getValue()));
    }
    return new ShoppingCartView(id, items, summary.getCheckoutDate());
  }
  // #shopping-cart-service-map
}
