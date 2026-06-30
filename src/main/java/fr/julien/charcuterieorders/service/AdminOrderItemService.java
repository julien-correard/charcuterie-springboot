package fr.julien.charcuterieorders.service;

import fr.julien.charcuterieorders.model.*;
import fr.julien.charcuterieorders.repository.AdminOrderItemRepository;
import fr.julien.charcuterieorders.repository.OrderItemRepository;
import fr.julien.charcuterieorders.repository.ProductRepository;
import fr.julien.charcuterieorders.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    public List<AdminOrderItem> getAll() {
        return adminOrderItemRepository.findAll();
    }

    private static final Logger log =
            LoggerFactory.getLogger(AdminOrderItemService.class);



    public void saveOrUpdate(Long userId, Long productId, Integer quantity, Integer doneQuantity) {

        log.info("SAVE START userId={} productId={} qty={} done={}",
                userId, productId, quantity, doneQuantity);

        User user = userRepository.findById(userId).orElseThrow();
        Product product = productRepository.findById(productId).orElseThrow();

        OrderItemId id = new OrderItemId(userId, productId);

        if (quantity == 0) {
            log.warn("DELETE userId={} productId={}", userId, productId);
            adminOrderItemRepository.deleteById(id);
            return;
        }

        AdminOrderItem item = adminOrderItemRepository
                .findById(id)
                .orElse(new AdminOrderItem(id, user, product, 0, 0));

        log.info("BEFORE dbQty={} dbDone={}", item.getQuantity(), item.getDoneQuantity());

        item.setQuantity(quantity);
        item.setDoneQuantity(doneQuantity);

        adminOrderItemRepository.save(item);

        log.info("END SAVE dbQty={} dbDone={}", item.getQuantity(), item.getDoneQuantity());
    }

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

            int doneQuantity = (previous != null)
                    ? previous.getDoneQuantity()
                    : 0;

            AdminOrderItem adminItem = new AdminOrderItem(
                    id,
                    item.getUser(),
                    item.getProduct(),
                    item.getQuantity(),
                    doneQuantity
            );

            toSave.add(adminItem);
        }

        adminOrderItemRepository.saveAll(toSave);

        List<AdminOrderItem> orphans = existing.values()
                .stream()
                .filter(adminItem -> !validIds.contains(adminItem.getId()))
                .toList();

        adminOrderItemRepository.deleteAll(orphans);

    }


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

            int doneQuantity = (previous != null)
                    ? previous.getDoneQuantity()
                    : 0;

            AdminOrderItem adminItem = new AdminOrderItem(
                    id,
                    item.getUser(),
                    item.getProduct(),
                    item.getQuantity(),
                    doneQuantity
            );

            toSave.add(adminItem);
        }

        adminOrderItemRepository.saveAll(toSave);

        List<AdminOrderItem> orphans = existing.values()
                .stream()
                .filter(adminItem -> !validIds.contains(adminItem.getId()))
                .toList();

        adminOrderItemRepository.deleteAll(orphans);
    }



    public void resetAll() {
        adminOrderItemRepository.deleteAll();
    }
}