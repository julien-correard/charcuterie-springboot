package fr.julien.charcuterieorders.service;

import fr.julien.charcuterieorders.dto.OrderLineForm;
import fr.julien.charcuterieorders.model.*;
import fr.julien.charcuterieorders.repository.AdminOrderItemRepository;
import fr.julien.charcuterieorders.repository.OrderItemRepository;
import fr.julien.charcuterieorders.repository.ProductRepository;
import fr.julien.charcuterieorders.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class AdminOrderItemService {

    private final AdminOrderItemRepository adminOrderItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    private static final Logger log =
            LoggerFactory.getLogger(AdminOrderItemService.class);

    public List<AdminOrderItem> getAll() {
        return adminOrderItemRepository.findAll();
    }

    /**
     * Sauvegarde/suppression d'un seul item. Conservé pour l'édition unitaire
     * (endpoint /item), qui reste une opération légitime sur une seule cellule.
     * Passe par la logique batch en interne pour ne pas dupliquer le code.
     */
    @Transactional
    public void saveOrUpdate(Long userId, Long productId, Integer quantity, Integer doneQuantity) {

        OrderLineForm form = new OrderLineForm();
        form.setUserId(userId);
        form.setProductId(productId);
        form.setQuantity(quantity);
        form.setDoneQuantity(doneQuantity);

        saveOrUpdateBatch(List.of(form));
    }

    /**
     * Sauvegarde en VRAI batch : au lieu d'un aller-retour réseau par produit
     * (findUser + findProduct + findItem + save, x4 par ligne), on précharge
     * tout en 3-4 requêtes group ées, on applique les changements en mémoire,
     * puis on écrit tout en une seule fois. Le tout dans une seule transaction,
     * ce qui élimine aussi la race condition entre requêtes concurrentes sur
     * la même ligne (elles sont maintenant sérialisées par la transaction/le
     * verrou optimiste ci-dessous, au lieu de s'entrelacer en lectures sales).
     */
    @Transactional
    public void saveOrUpdateBatch(List<OrderLineForm> items) {

        if (items == null || items.isEmpty()) {
            return;
        }

        log.info("BATCH SAVE START size={}", items.size());

        Set<Long> userIds = items.stream()
                .map(OrderLineForm::getUserId)
                .collect(Collectors.toSet());

        Set<Long> productIds = items.stream()
                .map(OrderLineForm::getProductId)
                .collect(Collectors.toSet());

        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Map<Long, Product> productsById = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<OrderItemId> ids = items.stream()
                .map(i -> new OrderItemId(i.getUserId(), i.getProductId()))
                .distinct()
                .toList();

        Map<OrderItemId, AdminOrderItem> existingById = adminOrderItemRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(AdminOrderItem::getId, Function.identity()));

        List<AdminOrderItem> toSave = new ArrayList<>();
        List<AdminOrderItem> toDelete = new ArrayList<>();

        for (OrderLineForm form : items) {

            OrderItemId id = new OrderItemId(form.getUserId(), form.getProductId());
            AdminOrderItem existing = existingById.get(id);

            if (form.getQuantity() == null || form.getQuantity() == 0) {
                if (existing != null) {
                    toDelete.add(existing);
                }
                continue;
            }

            User user = usersById.get(form.getUserId());
            Product product = productsById.get(form.getProductId());

            if (user == null || product == null) {
                log.warn("BATCH SAVE SKIP userId={} productId={} (utilisateur ou produit introuvable)",
                        form.getUserId(), form.getProductId());
                continue;
            }

            AdminOrderItem item = (existing != null)
                    ? existing
                    : new AdminOrderItem(id, user, product, 0, 0);

            item.setQuantity(form.getQuantity());
            item.setDoneQuantity(form.getDoneQuantity());

            toSave.add(item);
        }

        if (!toDelete.isEmpty()) {
            adminOrderItemRepository.deleteAllInBatch(toDelete);
        }

        if (!toSave.isEmpty()) {
            adminOrderItemRepository.saveAll(toSave);
        }

        log.info("BATCH SAVE END saved={} deleted={}", toSave.size(), toDelete.size());
    }

    @Transactional
    public void syncByUser(Long userId) {

        Map<OrderItemId, AdminOrderItem> existing =
                adminOrderItemRepository.findByUserId(userId)
                        .stream()
                        .collect(Collectors.toMap(
                                AdminOrderItem::getId,
                                Function.identity()
                        ));

        List<AdminOrderItem> toSave = new ArrayList<>();
        Set<OrderItemId> validIds = new HashSet<>();

        List<OrderItem> userItems = orderItemRepository.findByUserId(userId);

        for (OrderItem item : userItems) {

            OrderItemId id = new OrderItemId(
                    item.getUser().getId(),
                    item.getProduct().getId()
            );

            validIds.add(id);

            AdminOrderItem previous = existing.get(id);
            int doneQuantity = (previous != null) ? previous.getDoneQuantity() : 0;

            toSave.add(new AdminOrderItem(
                    id, item.getUser(), item.getProduct(), item.getQuantity(), doneQuantity
            ));
        }

        adminOrderItemRepository.saveAll(toSave);

        List<AdminOrderItem> orphans = existing.values()
                .stream()
                .filter(adminItem -> !validIds.contains(adminItem.getId()))
                .toList();

        // deleteAllInBatch = un seul DELETE ... WHERE id IN (...), au lieu
        // d'un DELETE par ligne (c'était la cause de la synchro qui prenait
        // plus d'une minute pour une trentaine de lignes).
        adminOrderItemRepository.deleteAllInBatch(orphans);
    }

    @Transactional
    public void syncFromOrderItems(List<OrderItem> orderItems) {

        Map<OrderItemId, AdminOrderItem> existing =
                adminOrderItemRepository.findAll()
                        .stream()
                        .collect(Collectors.toMap(
                                AdminOrderItem::getId,
                                Function.identity()
                        ));

        List<AdminOrderItem> toSave = new ArrayList<>();
        Set<OrderItemId> validIds = new HashSet<>();

        for (OrderItem item : orderItems) {

            OrderItemId id = new OrderItemId(
                    item.getUser().getId(),
                    item.getProduct().getId()
            );

            validIds.add(id);

            AdminOrderItem previous = existing.get(id);
            int doneQuantity = (previous != null) ? previous.getDoneQuantity() : 0;

            toSave.add(new AdminOrderItem(
                    id, item.getUser(), item.getProduct(), item.getQuantity(), doneQuantity
            ));
        }

        adminOrderItemRepository.saveAll(toSave);

        List<AdminOrderItem> orphans = existing.values()
                .stream()
                .filter(adminItem -> !validIds.contains(adminItem.getId()))
                .toList();

        adminOrderItemRepository.deleteAllInBatch(orphans);
    }

    @Transactional
    public void resetAll() {
        // deleteAllInBatch() sans argument = un seul "DELETE FROM admin_order_items",
        // au lieu de charger toutes les lignes puis les supprimer une par une.
        adminOrderItemRepository.deleteAllInBatch();
    }
}
