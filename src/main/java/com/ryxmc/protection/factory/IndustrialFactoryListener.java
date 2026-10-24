package com.ryxmc.protection.factory;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * IndustrialFactoryListener v1.3
 *
 * ── [FIX CRÍTICO] Duplicación al romper Tolva o Dropper ──────────────────
 *
 *  ANTES: onBlockBreak solo comprobaba si el bloque roto era el CORAZÓN
 *  (HEART_MATERIAL). Si el jugador rompía la TOLVA o el DROPPER
 *  directamente, el evento NO se interceptaba — Minecraft dropeaba un
 *  HOPPER/DROPPER vanilla gratis (nunca comprado) y la fábrica quedaba
 *  registrada con la estructura incompleta, permitiendo repetir el
 *  proceso y duplicar bloques indefinidamente.
 *
 *  AHORA: se usa IndustrialFactoryManager.getByAnyBlock(loc), que
 *  identifica la fábrica sin importar CUÁL de los 3 bloques (corazón,
 *  tolva o dropper) fue el que se rompió. Rompas lo que rompas de los
 *  3, se llama a manager.remove() que destruye TODA la estructura,
 *  solo dropea el ítem del corazón (nunca hopper/dropper sueltos) y
 *  limpia los 3 índices — imposible duplicar.
 *
 * ── [NUEVO] Panel real en vez de mensajes de chat ─────────────────────────
 *  openIndustrialGui() ahora abre IndustrialFactoryGui en vez de solo
 *  imprimir texto — panel visual con selector de producto y mejoras.
 */
public class IndustrialFactoryListener implements Listener {

    private final RyxProtection plugin;

    public IndustrialFactoryListener(RyxProtection plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────────
    //  Colocación — Corazón industrial, Rellenador, Tubo, Diana, Combinador
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player    p    = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();

        // ── Corazón de fábrica industrial ────────────────────────────────────
        if (IndustrialFactory.isHeartItem(hand)) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                event.setCancelled(true);
                Block clicked = event.getClickedBlock();

                if (clicked.getType() == IndustrialFactory.HEART_MATERIAL
                        && plugin.getIndustrialFactoryManager().isHeartBlock(clicked.getLocation())) {
                    openIndustrialGui(p, clicked);
                    return;
                }

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    boolean placed = plugin.getIndustrialFactoryManager().tryPlace(p, clicked);
                    if (placed) hand.setAmount(hand.getAmount() - 1);
                }, 1L);
                return;
            }

            if (event.getAction() == Action.RIGHT_CLICK_AIR
                    || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                if (!plugin.getIndustrialFactoryManager().hasPreview(p.getUniqueId())) {
                    plugin.getIndustrialFactoryManager().startPreview(p);
                    p.sendMessage(Txt.info("§7Apunta a una superficie para ver la vista previa."
                            + " §8Azul=Tolva, §aVerde=Corazón, §cRojo=Dropper"));
                }
            }
            return;
        }

        // ── Diana Direccional — colocar o ciclar dirección ───────────────────
        if (TubeNetwork.isDirectionalItem(hand)) {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
            event.setCancelled(true);
            Block clickedBlock = event.getClickedBlock();

            if (clickedBlock.getType() == TubeNetwork.DIRECTIONAL_MATERIAL
                    && plugin.getTubeManager().isDirectional(clickedBlock.getLocation())) {
                BlockFace newFace = plugin.getTubeManager().cycleDirectional(clickedBlock.getLocation());
                p.sendMessage(Txt.ok("§c◆ Diana §aredirigida hacia §f" + newFace.name() + "§a."));
                spawnDirectionIndicator(clickedBlock, newFace);
                return;
            }

            Block place = clickedBlock.getRelative(event.getBlockFace());
            if (!place.getType().isAir()) {
                p.sendMessage(Txt.err("No puedes colocar una diana ahí — el espacio está ocupado."));
                return;
            }

            BlockFace initialFace = playerHorizontalFacing(p);
            final Block finalPlace = place;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!finalPlace.getType().isAir()) return;
                finalPlace.setType(TubeNetwork.DIRECTIONAL_MATERIAL, false);
                plugin.getTubeManager().registerDirectional(finalPlace.getLocation(), initialFace);
                hand.setAmount(hand.getAmount() - 1);

                World w = finalPlace.getWorld();
                if (w != null) w.playSound(finalPlace.getLocation(),
                        Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 0.8f);

                p.sendMessage(Txt.ok("§c◆ Diana §acolocada §8— salida inicial: §f" + initialFace.name()));
                p.sendMessage(Txt.info("§7Clic derecho con §fmano vacía §7sobre la diana para cambiar su dirección."));
            }, 1L);
            return;
        }

        // ── Combinador — colocar o rotar boquilla (5 entradas → 1 salida) ────
        if (TubeNetwork.isCombinerItem(hand)) {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
            event.setCancelled(true);
            Block clickedBlock = event.getClickedBlock();

            if (clickedBlock.getType() == TubeNetwork.COMBINER_MATERIAL
                    && plugin.getTubeManager().isCombiner(clickedBlock.getLocation())) {
                BlockFace newFace = plugin.getTubeManager().cycleCombiner(clickedBlock.getLocation());
                p.sendMessage(Txt.ok("§6▼ Combinador §8— §aboquilla rotada hacia §f" + newFace.name() + "§a."));
                return;
            }

            Block place = clickedBlock.getRelative(event.getBlockFace());
            if (!place.getType().isAir()) {
                p.sendMessage(Txt.err("No puedes colocar un combinador ahí — el espacio está ocupado."));
                return;
            }

            BlockFace initialFace = playerHorizontalFacing(p);
            final Block finalPlace = place;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!finalPlace.getType().isAir()) return;
                finalPlace.setType(TubeNetwork.COMBINER_MATERIAL, false);
                plugin.getTubeManager().registerCombiner(finalPlace.getLocation(), initialFace);
                plugin.getTubeManager().applyCombinerBlockFacing(finalPlace.getLocation(), initialFace);
                hand.setAmount(hand.getAmount() - 1);

                World w = finalPlace.getWorld();
                if (w != null) w.playSound(finalPlace.getLocation(),
                        Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.2f);

                p.sendMessage(Txt.ok("§6▼ Combinador §acolocado §8— boquilla (salida): §f" + initialFace.name()));
                p.sendMessage(Txt.info("§7Las otras §a5 caras §7aceptan entrada. Clic derecho con §fmano vacía"
                        + " §7para rotar la boquilla."));
            }, 1L);
            return;
        }

        // ── Ciclar dirección de Diana o Combinador con MANO VACÍA ────────────
        if (hand.getType().isAir()
                && event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null) {
            Block clicked = event.getClickedBlock();

            if (clicked.getType() == TubeNetwork.DIRECTIONAL_MATERIAL
                    && plugin.getTubeManager().isDirectional(clicked.getLocation())) {
                event.setCancelled(true);
                BlockFace newFace = plugin.getTubeManager().cycleDirectional(clicked.getLocation());
                p.sendMessage(Txt.ok("§c◆ Diana §aredirigida hacia §f" + newFace.name() + "§a."));
                spawnDirectionIndicator(clicked, newFace);
                return;
            }

            if (clicked.getType() == TubeNetwork.COMBINER_MATERIAL
                    && plugin.getTubeManager().isCombiner(clicked.getLocation())) {
                event.setCancelled(true);
                BlockFace newFace = plugin.getTubeManager().cycleCombiner(clicked.getLocation());
                p.sendMessage(Txt.ok("§6▼ Combinador §8— §aboquilla rotada hacia §f" + newFace.name() + "§a."));
                return;
            }
        }

        // ── Colocación de TUBO con auto-orientación ──────────────────────────
        if (TubeNetwork.isTubeItem(hand)) {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;

            event.setCancelled(true);

            Block clickedBlock = event.getClickedBlock();
            Block place = clickedBlock.getRelative(event.getBlockFace());

            if (!place.getType().isAir()) {
                p.sendMessage(Txt.err("No puedes colocar un tubo ahí — el espacio está ocupado."));
                return;
            }

            final Block finalPlace = place;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!finalPlace.getType().isAir()) return;

                plugin.getTubeManager().placeTube(finalPlace);
                hand.setAmount(hand.getAmount() - 1);

                World w = finalPlace.getWorld();
                if (w != null) {
                    w.spawnParticle(Particle.ELECTRIC_SPARK,
                            finalPlace.getLocation().add(0.5, 0.5, 0.5), 8, 0.15, 0.15, 0.15, 0.02);
                    w.playSound(finalPlace.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.6f);
                }
            }, 1L);
            return;
        }

        // ── Clic derecho sobre corazón SIN ítem corazón en mano → panel ──────
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block clicked = event.getClickedBlock();
            if (clicked.getType() == IndustrialFactory.HEART_MATERIAL
                    && plugin.getIndustrialFactoryManager().isHeartBlock(clicked.getLocation())) {
                event.setCancelled(true);
                openIndustrialGui(p, clicked);
                return;
            }

            if (clicked.getType() == FillerFactory.FILLER_MATERIAL
                    && plugin.getFillerManager().isFillerLocation(clicked.getLocation())) {
                event.setCancelled(true);
                openFillerInfo(p, clicked);
                return;
            }
        }

        // ── Colocar Rellenador ────────────────────────────────────────────────
        if (FillerFactory.isFillerItem(hand)
                && event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null) {
            event.setCancelled(true);
            Block place = event.getClickedBlock().getRelative(event.getBlockFace());
            if (!place.getType().isAir()) {
                p.sendMessage(Txt.err("No puedes colocar el rellenador ahí — el espacio está ocupado."));
                return;
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!place.getType().isAir()) return;
                place.setType(FillerFactory.FILLER_MATERIAL, false);
                plugin.getFillerManager().register(p, place.getLocation());
                hand.setAmount(hand.getAmount() - 1);
            }, 1L);
        }
    }

    // ─────────────────────────────────────────────────
    //  Romper bloques del nuevo sistema
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block  b = event.getBlock();
        Player p = event.getPlayer();

        // ── [FIX CRÍTICO] Romper CUALQUIERA de los 3 bloques de la fábrica ────
        // industrial (corazón, tolva o dropper) destruye TODA la estructura.
        // Antes solo se comprobaba el corazón — romper tolva/dropper directamente
        // dropeaba un HOPPER/DROPPER vanilla gratis sin destruir el registro,
        // permitiendo duplicación.
        if (b.getType() == IndustrialFactory.HEART_MATERIAL
                || b.getType() == IndustrialFactory.HOPPER_MATERIAL
                || b.getType() == IndustrialFactory.DROPPER_MATERIAL) {

            IndustrialFactory f = plugin.getIndustrialFactoryManager().getByAnyBlock(b.getLocation());
            if (f != null) {
                event.setCancelled(true);
                if (!f.getOwnerUUID().equals(p.getUniqueId()) && !p.hasPermission("ryx.admin")) {
                    p.sendMessage(Txt.err("No puedes romper la fábrica industrial de §e" + f.getOwnerName() + "§c."));
                    return;
                }
                // remove() acepta CUALQUIER bloque de los 3 y limpia todo
                plugin.getIndustrialFactoryManager().remove(b, p);
                return;
            }
            // Si no pertenece a ninguna fábrica registrada, es un HOPPER/DROPPER
            // vanilla normal — comportamiento intacto, no interferir.
        }

        // ── Romper rellenador ─────────────────────────────────────────────────
        if (b.getType() == FillerFactory.FILLER_MATERIAL
                && plugin.getFillerManager().isFillerLocation(b.getLocation())) {
            event.setCancelled(true);
            FillerFactory f = plugin.getFillerManager().getByLocation(b.getLocation());
            if (f != null && !f.getOwnerUUID().equals(p.getUniqueId())
                    && !p.hasPermission("ryx.admin")) {
                p.sendMessage(Txt.err("No puedes romper el rellenador de §e" + f.getOwnerName() + "§c."));
                return;
            }
            b.setType(Material.AIR, false);
            plugin.getFillerManager().unregister(b.getLocation());
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5),
                    FillerFactory.buildItem());
            if (p != null) p.sendMessage(Txt.ok("Rellenador §celiminado§a."));
            return;
        }

        // ── Romper tubo de conductividad ──────────────────────────────────────
        if (b.getType() == TubeNetwork.TUBE_MATERIAL) {
            if (plugin.getTubeManager().isTube(b.getLocation())) {
                event.setDropItems(false);
                plugin.getTubeManager().unregister(b.getLocation());
                b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5),
                        TubeNetwork.buildTubeItem());
            }
        }

        // ── Romper Diana Direccional ──────────────────────────────────────────
        if (b.getType() == TubeNetwork.DIRECTIONAL_MATERIAL) {
            if (plugin.getTubeManager().isDirectional(b.getLocation())) {
                event.setDropItems(false);
                plugin.getTubeManager().unregisterDirectional(b.getLocation());
                b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5),
                        TubeNetwork.buildDirectionalItem());
            }
        }

        // ── Romper Combinador ──────────────────────────────────────────────────
        if (b.getType() == TubeNetwork.COMBINER_MATERIAL) {
            if (plugin.getTubeManager().isCombiner(b.getLocation())) {
                event.setDropItems(false);
                plugin.getTubeManager().unregisterCombiner(b.getLocation());
                b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5),
                        TubeNetwork.buildCombinerItem());
            }
        }
    }

    // ─────────────────────────────────────────────────
    //  Preview — cancelar cuando cambia slot o suelta ítem
    // ─────────────────────────────────────────────────

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        plugin.getIndustrialFactoryManager().stopPreview(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        plugin.getIndustrialFactoryManager().stopPreview(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getIndustrialFactoryManager().stopPreview(event.getPlayer().getUniqueId());
    }

    // ─────────────────────────────────────────────────
    //  Paneles
    // ─────────────────────────────────────────────────

    /** [NUEVO] Abre el panel visual real en vez de solo enviar mensajes de chat. */
    private void openIndustrialGui(Player p, Block heartBlock) {
        IndustrialFactory f = plugin.getIndustrialFactoryManager()
                .getByHeartLoc(heartBlock.getLocation());
        if (f == null) return;
        plugin.getIndustrialFactoryGui().open(p, f);
    }

    private void openFillerInfo(Player p, Block fillerBlock) {
        FillerFactory f = plugin.getFillerManager().getByLocation(fillerBlock.getLocation());
        if (f == null) return;
        p.sendMessage(Txt.SEP);
        p.sendMessage("  §6§l⚡ §f§lRellenador §8— §e" + f.getOwnerName());
        p.sendMessage(Txt.SEP);
        p.sendMessage("§9Estado:        " + f.getStateDisplay());
        p.sendMessage("§9Ítems enviados: §f" + f.getItemsFilled());
        p.sendMessage(Txt.SEP);
        p.sendMessage("§7Coloca cofres/barriles/shulkers en §fcualquier lado§7,");
        p.sendMessage("§7o conéctalos con §bTubos §7a las fábricas/compresores.");
        p.sendMessage(Txt.SEP);
    }

    // ─────────────────────────────────────────────────
    //  Helpers de Diana y Combinador
    // ─────────────────────────────────────────────────

    private BlockFace playerHorizontalFacing(Player p) {
        float yaw = p.getLocation().getYaw();
        if (yaw < 0) yaw += 360;
        if (yaw < 45 || yaw >= 315) return BlockFace.SOUTH;
        if (yaw < 135) return BlockFace.WEST;
        if (yaw < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    private void spawnDirectionIndicator(Block b, BlockFace face) {
        World w = b.getWorld();
        if (w == null) return;
        Location center = b.getLocation().add(0.5, 0.5, 0.5);
        for (int i = 1; i <= 4; i++) {
            Location pt = center.clone().add(
                    face.getModX() * i * 0.25,
                    face.getModY() * i * 0.25,
                    face.getModZ() * i * 0.25);
            w.spawnParticle(Particle.END_ROD, pt, 1, 0, 0, 0, 0);
        }
        w.playSound(center, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.8f);
    }
}
