package com.blank038.servermarket.data.gui;

import com.blank038.servermarket.ServerMarket;
import com.blank038.servermarket.i18n.I18n;
import com.blank038.servermarket.data.PlayerData;
import com.blank038.servermarket.util.CommonUtil;
import com.mc9y.blank038api.util.inventory.GuiModel;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author Blank038
 */
public class StoreContainer {
    private final Player PLAYER_TAR;
    private final int MARKET_PAGE;
    private final String OLD_MARKET;

    public StoreContainer(Player player, int marketPage, String oldMarket) {
        this.PLAYER_TAR = player;
        this.MARKET_PAGE = marketPage;
        this.OLD_MARKET = oldMarket;
    }

    public void open(int currentPage) {
        // 指向文件
        File file = new File(ServerMarket.getInstance().getDataFolder(), "store.yml");
        // 读取配置文件
        FileConfiguration data = YamlConfiguration.loadConfiguration(file);
        GuiModel guiModel = new GuiModel(data.getString("title"), data.getInt("size"));
        // 设置界面状态
        guiModel.registerListener(ServerMarket.getInstance());
        guiModel.setCloseRemove(true);
        // 设置物品
        HashMap<Integer, ItemStack> items = new HashMap<>();
        // 开始遍历设置物品
        if (data.contains("items")) {
            for (String key : data.getConfigurationSection("items").getKeys(false)) {
                ConfigurationSection section = data.getConfigurationSection("items." + key);
                ItemStack itemStack = new ItemStack(Material.valueOf(section.getString("type").toUpperCase()),
                        section.getInt("amount"), (short) section.getInt("data"));
                ItemMeta itemMeta = itemStack.getItemMeta();
                itemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', section.getString("name")));
                // 开始遍历设置Lore
                List<String> list = new ArrayList<>();
                for (String lore : section.getStringList("lore")) {
                    list.add(ChatColor.translateAlternateColorCodes('&', lore));
                }
                itemMeta.setLore(list);
                itemStack.setItemMeta(itemMeta);
                // 开始判断是否有交互操作
                if (section.contains("action")) {
                    itemStack = ServerMarket.getInstance().getNBTBase().addTag(itemStack, "action", section.getString("action"));
                }
                // 开始判断槽位方向
                for (int i : CommonUtil.formatSlots(section.getString("slot"))) {
                    items.put(i, itemStack);
                }
            }
        }
        // 开始获取玩家仓库
        PlayerData playerData = ServerMarket.getInstance().getApi().getPlayerData(PLAYER_TAR.getUniqueId());
        Integer[] slots = CommonUtil.formatSlots(data.getString("store-item-slots"));
        HashMap<String, ItemStack> storeItems = playerData.getItems();
        String[] keys = storeItems.keySet().toArray(new String[0]);
        // 计算下标
        int start = slots.length * (currentPage - 1), end = slots.length * currentPage;
        for (int i = start, index = 0; i < end; i++, index++) {
            if (index >= slots.length || i >= keys.length) {
                break;
            }
            items.put(slots[index], ServerMarket.getInstance().getNBTBase().addTag(storeItems.get(keys[i]), "StoreID", keys[i]));
        }
        // 界面物品设置结束
        guiModel.setItem(items);
        guiModel.execute((e) -> {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getInventory()) {
                // 获取点击的玩家
                Player clicker = (Player) e.getWhoClicked();
                // 获取点击的物品和物品的Tag
                ItemStack itemStack = e.getCurrentItem();
                String storeId = ServerMarket.getInstance().getNBTBase().get(itemStack, "StoreID"),
                        action = ServerMarket.getInstance().getNBTBase().get(itemStack, "action");
                if ("market".equalsIgnoreCase(action)) {
                    ServerMarket.getInstance().getApi().openMarket(clicker, this.OLD_MARKET, this.MARKET_PAGE);
                } else if (storeId != null) {
                    this.getItem(clicker, storeId);
                }
            }
        });
        guiModel.openInventory(PLAYER_TAR);
    }

    public void getItem(Player player, String uuid) {
        PlayerData data = ServerMarket.getInstance().getApi().getPlayerData(player.getUniqueId());
        if (data.contains(uuid)) {
            if (player.getInventory().firstEmpty() == -1) {
                player.sendMessage(I18n.getString("inventory-full", true));
                return;
            }
            // 基于玩家物品
            ItemStack itemStack = PlayerData.PLAYER_DATA.get(player.getUniqueId()).remove(uuid);
            String displayMmae = itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName() ?
                    itemStack.getItemMeta().getDisplayName() : itemStack.getType().name();
            player.getInventory().addItem(itemStack);
            player.sendMessage(I18n.getString("get-store-item", true).replace("%item%", displayMmae)
                    .replace("%amount%", String.valueOf(itemStack.getAmount())));
            // 刷新玩家界面
            ServerMarket.getInstance().getApi().openMarket(player, this.OLD_MARKET, this.MARKET_PAGE);
        } else {
            player.sendMessage(I18n.getString("error-store", true));
        }
    }
}
