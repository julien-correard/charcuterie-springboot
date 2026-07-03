package fr.julien.charcuterieorders.controller.admin;

import fr.julien.charcuterieorders.model.OrderItem;
import fr.julien.charcuterieorders.model.Product;
import fr.julien.charcuterieorders.model.User;
import fr.julien.charcuterieorders.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/commandes/edit/")
@RequiredArgsConstructor

public class AdminOrderEditController {

    private final OrderService orderService;
    private final UserService userService;
    private final ProductService productService;
    private final OrderItemService orderItemService;
    private final AdminOrderItemService adminOrderItemService;


    @GetMapping("/{id}")
    public String index(@PathVariable Long id, Model model) {

        User user = userService.getById(id);

        // Produits accessibles groupés par catégorie
        Map<String, List<Product>> productsByCategory = user.getAccessibleProducts()
                .stream()
                .sorted(Comparator.comparing(Product::getName))
                .collect(Collectors.groupingBy(Product::getCategory));
        // Quantités existantes indexées par productId pour affichage facile
        Map<Long, Integer> quantities = orderItemService.getByUser(user)
                .stream()
                .collect(Collectors.toMap(
                        item -> item.getProduct().getId(),
                        OrderItem::getQuantity
                ));

        model.addAttribute("productsByCategory",
                productService.groupByCategory(user.getAccessibleProducts()));
        model.addAttribute("quantities", quantities);
        model.addAttribute("client", user);
        return "admin/commandes/edit";
    }

    @Transactional
    @PostMapping("/{id}")
    public String store(@PathVariable Long id, @RequestParam Map<String, String> formData, RedirectAttributes redirectAttributes) {

        User user = userService.getById(id);

        List<OrderItem> items = orderItemService.getByUser(user);

        Map<Long, Integer> dbQuantities = items.stream()
                .collect(Collectors.toMap(
                        item -> item.getProduct().getId(),
                        OrderItem::getQuantity
                ));

        // Map productId -> Product construite une seule fois, au lieu d'un
        // .stream().filter().findFirst() par produit dans la boucle plus bas
        Map<Long, Product> productsById = user.getAccessibleProducts()
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (Long productId : productsById.keySet()) {
            dbQuantities.putIfAbsent(productId, 0);
        }

        Map<Long, Integer> formQuantities = new HashMap<>();

        formData.forEach((key, value) -> {
            if (key.startsWith("product_")) {

                Long productId = Long.parseLong(key.replace("product_", ""));
                Integer quantity = value.isBlank() ? 0 : Integer.parseInt(value);

                formQuantities.put(productId, quantity);

            }
        });

        boolean changed = !formQuantities.equals(dbQuantities);

        if (!changed) {
            redirectAttributes.addFlashAttribute("error", "Aucune modification détectée sur la commande, enregistrement impossible");
            return "redirect:/admin/commandes";
        }

        // Un seul aller-retour groupé (saveAll/deleteAllInBatch) au lieu d'un
        // findById + save/delete par produit. C'était la cause principale
        // des timeouts sur l'édition de commande.
        orderItemService.saveOrUpdateBatch(user, items, productsById, formQuantities);

        adminOrderItemService.syncByUser(id);

        redirectAttributes.addFlashAttribute("success", "Commande enregistrée");

        return String.format("redirect:/admin/commandes/edit/%d", id);
    }
}