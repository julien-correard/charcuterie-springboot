package fr.julien.charcuterieorders.service;

import fr.julien.charcuterieorders.model.OrderItem;
import fr.julien.charcuterieorders.model.OrderItemId;
import fr.julien.charcuterieorders.model.Product;
import fr.julien.charcuterieorders.model.User;
import fr.julien.charcuterieorders.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor

public class OrderItemService {

    private final OrderItemRepository orderItemRepository;

    public List<OrderItem> getByUser(User user) {
        return orderItemRepository.findByUser(user);
    }

    public Integer getQuantity(User user, Product product) {

        return orderItemRepository.findByUserAndProduct(user, product)
                .map(OrderItem::getQuantity)
                .orElse(0);
    }


    // ≈ updateOrInsert en Laravel
    // Conservée pour un usage ponctuel (une seule ligne). Ne PAS appeler
    // en boucle : chaque appel fait 1 findById + 1 save/delete, donc N
    // allers-retours DB pour N produits. Pour éditer plusieurs lignes
    // d'un coup (ex: formulaire de commande complet), utiliser
    // saveOrUpdateBatch ci-dessous.
    public void saveOrUpdate(User user, Product product, Integer quantity) {
        OrderItemId id = new OrderItemId(user.getId(), product.getId());

        OrderItem item = orderItemRepository.findById(id)
                .orElse(new OrderItem(id, user, product, null));

        if (quantity == null || quantity == 0) {
            orderItemRepository.delete(item);  // quantité vide = suppression
        } else {
            item.setQuantity(quantity);
            orderItemRepository.save(item);
        }
    }

    /**
     * Version batch de saveOrUpdate : au lieu d'un findById + save/delete
     * par produit, on part des items déjà chargés en mémoire (existingItems,
     * généralement déjà récupérés par l'appelant via getByUser), on applique
     * les changements en RAM, puis on écrit tout en 2 requêtes groupées max
     * (un saveAll + un deleteAllInBatch), dans une seule transaction.
     *
     * @param user           l'utilisateur concerné
     * @param existingItems  les OrderItem déjà en base pour cet utilisateur
     *                       (évite un findById par produit)
     * @param productsById   map productId -> Product déjà chargée (évite un
     *                       scan .stream().filter() par produit)
     * @param formQuantities map productId -> quantité soumise par le formulaire
     */
    @Transactional
    public void saveOrUpdateBatch(User user,
                                  List<OrderItem> existingItems,
                                  Map<Long, Product> productsById,
                                  Map<Long, Integer> formQuantities) {

        if (formQuantities == null || formQuantities.isEmpty()) {
            return;
        }

        Map<Long, OrderItem> existingByProductId = existingItems.stream()
                .collect(Collectors.toMap(
                        item -> item.getProduct().getId(),
                        Function.identity()
                ));

        List<OrderItem> toSave = new ArrayList<>();
        List<OrderItem> toDelete = new ArrayList<>();

        formQuantities.forEach((productId, quantity) -> {

            OrderItem existing = existingByProductId.get(productId);

            if (quantity == null || quantity == 0) {
                if (existing != null) {
                    toDelete.add(existing);
                }
                return;
            }

            Product product = productsById.get(productId);
            if (product == null) {
                return; // produit non accessible / introuvable
            }

            OrderItemId id = new OrderItemId(user.getId(), productId);
            OrderItem item = (existing != null)
                    ? existing
                    : new OrderItem(id, user, product, null);

            item.setQuantity(quantity);
            toSave.add(item);
        });

        if (!toDelete.isEmpty()) {
            orderItemRepository.deleteAllInBatch(toDelete);
        }
        if (!toSave.isEmpty()) {
            orderItemRepository.saveAll(toSave);
        }
    }

    public void resetAll() {
        orderItemRepository.deleteAll();
    }

    // Toutes les commandes pour la vue admin
    public List<OrderItem> getAll() {
        return orderItemRepository.findAll();
    }

    public boolean isOrdered(Product product) {
        return orderItemRepository.existsByProductId(product.getId());
    }

}