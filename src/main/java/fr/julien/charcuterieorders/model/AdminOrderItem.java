package fr.julien.charcuterieorders.model;

import jakarta.persistence.*;
import lombok.*;

// model/AdminOrderItem.java
@Entity
@Table(name = "admin_order_items")
@Getter
@Setter
@NoArgsConstructor
public class AdminOrderItem {

    @EmbeddedId
    private OrderItemId id; // tu réutilises le même embeddable

    @ManyToOne
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @MapsId("productId")
    @JoinColumn(name = "product_id")
    private Product product;

    private Integer quantity;

    @Column(name = "done_quantity", nullable = false)
    private int doneQuantity;

    // Verrou optimiste : si deux requêtes tentent de modifier la même ligne
    // en même temps, la seconde échoue avec une OptimisticLockException au
    // lieu d'écraser silencieusement la première. Filet de sécurité en plus
    // de la sérialisation faite dans AdminOrderItemService.
    @Version
    private Long version;

    // Constructeur explicite (remplace l'ancien @AllArgsConstructor) pour ne
    // pas casser les appels existants du type
    // "new AdminOrderItem(id, user, product, quantity, doneQuantity)".
    // version reste à null pour une nouvelle entité : c'est ce qu'attend
    // Hibernate pour détecter qu'il s'agit d'un INSERT et pas d'un UPDATE.
    public AdminOrderItem(OrderItemId id, User user, Product product,
                           Integer quantity, int doneQuantity) {
        this.id = id;
        this.user = user;
        this.product = product;
        this.quantity = quantity;
        this.doneQuantity = doneQuantity;
    }
}
