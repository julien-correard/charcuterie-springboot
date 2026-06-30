package fr.julien.charcuterieorders.controller.admin;

import fr.julien.charcuterieorders.dto.OrderForm;
import fr.julien.charcuterieorders.dto.OrderLineForm;
import fr.julien.charcuterieorders.model.AdminOrderItem;  // ← changé
import fr.julien.charcuterieorders.model.Order;
import fr.julien.charcuterieorders.model.Product;
import fr.julien.charcuterieorders.model.User;
import fr.julien.charcuterieorders.repository.AdminOrderItemRepository;
import fr.julien.charcuterieorders.repository.OrderItemRepository;
import fr.julien.charcuterieorders.service.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
@RequestMapping("/admin/commandes")
@RequiredArgsConstructor
public class AdminOrderController {

    private final UserService userService;
    private final ProductService productService;
    private final AdminOrderItemService adminOrderItemService;
    private final ExportService exportService;
    private final OrderItemService orderItemService;
    private final OrderItemRepository orderItemRepository;
    private final AdminOrderItemRepository adminOrderItemRepository;
    private final OrderService orderService;
    private static final Logger log =
            LoggerFactory.getLogger(AdminOrderController.class);

    @GetMapping
    public String index(Model model,
                        @RequestParam(defaultValue = "true") boolean seulementCommandes) {

        List<User> clients = new ArrayList<>(userService.getAllClients());

        clients.sort(
                Comparator
                        .comparing((User client) -> !"STOCK".equals(client.getInputMode()))
                        .thenComparing(User::getName)
        );

        List<AdminOrderItem> items = adminOrderItemService.getAll();  // ← changé

        Comparator<Product> triProduits = Comparator
                .comparingInt((Product p) -> ProductService.CATEGORY_ORDER.indexOf(p.getCategory()))
                .thenComparing(Product::getName);

        List<Product> products;
        if (seulementCommandes) {
            products = items.stream()
                    .map(AdminOrderItem::getProduct)  // ← changé
                    .distinct()
                    .sorted(triProduits)
                    .toList();
        } else {
            products = productService.getAllProducts();
        }

        Map<Long, Map<Long, Integer>> quantities = new HashMap<>();

        for (User client : clients) {
            Map<Long, Integer> productMap = new HashMap<>();
            for (Product product : products) {
                productMap.put(product.getId(), 0);
            }
            quantities.put(client.getId(), productMap);
        }

        Map<Long, Map<Long, Integer>> doneQuantities = new HashMap<>();

        for (AdminOrderItem item : items) {  // ← changé
            Long userId = item.getUser().getId();
            Long productId = item.getProduct().getId();
            quantities
                    .computeIfAbsent(userId, k -> new HashMap<>())
                    .put(productId, item.getQuantity());
            doneQuantities
                    .computeIfAbsent(userId, k -> new HashMap<>())
                    .put(productId, item.getDoneQuantity());
        }



        List<Order> orders = orderService.getAllOrders();

        model.addAttribute("clients", clients);
        model.addAttribute("products", products);
        model.addAttribute("quantities", quantities);
        model.addAttribute("doneQuantities", doneQuantities);
        model.addAttribute("seulementCommandes", seulementCommandes);
        model.addAttribute("orders", orders);
        return "admin/commandes/index";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() throws IOException {
        byte[] data = exportService.exportCommandes();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=commandes.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @PostMapping
    public ResponseEntity<Void> store(@RequestBody List<OrderLineForm> items) {

        items.forEach(System.out::println);

        for (OrderLineForm item : items) {
            adminOrderItemService.saveOrUpdate(
                    item.getUserId(),
                    item.getProductId(),
                    item.getQuantity(),
                    item.getDoneQuantity()
            );
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/sync")
    public String sync() {
        adminOrderItemService.syncFromOrderItems(orderItemService.getAll());
        return "redirect:/admin/commandes";
    }
    @PostMapping("/reset")
    public String reset() {
        adminOrderItemService.resetAll();
        orderItemService.resetAll();
        return "redirect:/admin/commandes";
    }

    @PostMapping("/{id}/delete")
    public String deleteOrder(@PathVariable Long id) {

        orderService.delete(id);

        return "redirect:/admin/commandes";
    }

    @PostMapping("/item")
    public ResponseEntity<Void> saveItem(@RequestBody OrderLineForm item) {

        log.info(
                "RECEIVED userId={} productId={} quantity={} doneQuantity={}",
                item.getUserId(),
                item.getProductId(),
                item.getQuantity(),
                item.getDoneQuantity()
        );

        adminOrderItemService.saveOrUpdate(
                item.getUserId(),
                item.getProductId(),
                item.getQuantity(),
                item.getDoneQuantity()
        );

        return ResponseEntity.ok().build();
    }
}